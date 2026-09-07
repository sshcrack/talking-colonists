package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ControlledTurnResult;
import me.sshcrack.mc_talking.api.conversation.ConversationTranscriptEntry;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Deterministic controlled-turn state machine; Minecraft/provider access is supplied by Hooks. */
final class ControlledConversationRuntime<P, A> {
    static final int MAX_TRANSCRIPT_CHARS = 8_000;

    interface Hooks<P, A> {
        void execute(@NotNull Runnable task);
        @NotNull UUID id(@NotNull P participant);
        @NotNull String name(@NotNull P participant);
        long gameTime(@NotNull P participant);
        @NotNull Availability availability(@NotNull P participant, @Nullable A audioAnchor);
        boolean hasCapacity();
        @NotNull StartResult start(@NotNull P participant, @NotNull String prompt,
                                   @NotNull PromptSessionContext promptContext,
                                   @Nullable A audioAnchor,
                                   @NotNull Consumer<AmbientLineResult> audibleCompletion);
        void cancel(@NotNull P participant, @NotNull UUID sessionId, @NotNull UUID turnId);
        boolean playerOwnsConversation(@NotNull P participant);
    }

    record Availability(boolean available, @Nullable ControlledTurnResult.FailureReason reason,
                        @NotNull String detail) {
        static @NotNull Availability ok() { return new Availability(true, null, "available"); }
        static @NotNull Availability rejected(@NotNull ControlledTurnResult.FailureReason reason,
                                              @NotNull String detail) {
            return new Availability(false, reason, detail);
        }
    }

    enum StartResult { STARTED, PROVIDER_UNAVAILABLE, SPEAKER_UNAVAILABLE, CAPACITY_EXHAUSTED, FAILED }

    private final UUID sessionId = UUID.randomUUID();
    private final Hooks<P, A> hooks;
    private final ControlledConversationOptions options;
    private final List<P> participants;
    private final Map<UUID, P> byId;
    private final AtomicReference<ControlledConversationSession.State> state =
            new AtomicReference<>(ControlledConversationSession.State.OPEN);
    private final AtomicReference<ActiveTurn<P>> activeTurn = new AtomicReference<>();
    private final ArrayDeque<ConversationTranscriptEntry> transcript = new ArrayDeque<>();
    private int transcriptChars;
    private volatile String agenda;

    ControlledConversationRuntime(@NotNull List<P> participants, @NotNull String agenda,
                                  @NotNull ControlledConversationOptions options,
                                  @NotNull Hooks<P, A> hooks) {
        if (participants.isEmpty()) throw new IllegalArgumentException("Controlled session requires participants");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.options = Objects.requireNonNull(options, "options");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
        LinkedHashMap<UUID, P> unique = new LinkedHashMap<>();
        for (P participant : participants) {
            if (participant == null) throw new IllegalArgumentException("participant must not be null");
            UUID id = Objects.requireNonNull(hooks.id(participant), "participant id");
            if (unique.putIfAbsent(id, participant) != null) {
                throw new IllegalArgumentException("Duplicate participant " + id);
            }
        }
        this.byId = Map.copyOf(unique);
        this.participants = List.copyOf(unique.values());
    }

    @NotNull UUID sessionId() { return sessionId; }
    @NotNull List<P> participants() { return participants; }
    @NotNull ControlledConversationSession.State state() { return state.get(); }

    void setAgenda(@NotNull String agenda) {
        if (state.get() == ControlledConversationSession.State.ENDED) throw new IllegalStateException("session ended");
        this.agenda = Objects.requireNonNull(agenda, "agenda");
    }

    void addTranscript(@NotNull ConversationTranscriptEntry entry) {
        if (state.get() == ControlledConversationSession.State.ENDED) throw new IllegalStateException("session ended");
        appendTranscript(entry);
    }

    @NotNull CompletableFuture<ControlledTurnResult> requestTurn(@NotNull P speaker,
                                                                  @NotNull String topicOrInstruction,
                                                                  @Nullable A audioAnchor) {
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(topicOrInstruction, "topicOrInstruction");
        UUID turnId = UUID.randomUUID();
        if (!byId.containsKey(hooks.id(speaker))) {
            return completeOnExecutor(ControlledTurnResult.rejected(
                    sessionId, turnId, ControlledTurnResult.FailureReason.SPEAKER_NOT_PARTICIPANT,
                    "speaker is not a session participant"));
        }
        if (state.get() == ControlledConversationSession.State.ENDED) {
            return completeOnExecutor(ControlledTurnResult.sessionEnded(sessionId, turnId, "session has ended"));
        }
        if (!state.compareAndSet(ControlledConversationSession.State.OPEN,
                                 ControlledConversationSession.State.TURN_ACTIVE)) {
            return completeOnExecutor(ControlledTurnResult.rejected(
                    sessionId, turnId, ControlledTurnResult.FailureReason.TURN_ALREADY_ACTIVE,
                    "another speaker already has the floor"));
        }

        CompletableFuture<ControlledTurnResult> future = new CompletableFuture<>();
        ActiveTurn<P> turn = new ActiveTurn<>(turnId, speaker, future);
        activeTurn.set(turn);
        String turnAgenda = agenda;
        hooks.execute(() -> startTurn(turn, topicOrInstruction, turnAgenda, audioAnchor));
        return future;
    }

    boolean interruptTurn() {
        ActiveTurn<P> turn = activeTurn.get();
        if (turn == null || !activeTurn.compareAndSet(turn, null)) return false;
        state.compareAndSet(ControlledConversationSession.State.TURN_ACTIVE,
                            ControlledConversationSession.State.OPEN);
        hooks.execute(() -> {
            turn.future().complete(ControlledTurnResult.interrupted(sessionId, turn.turnId()));
            hooks.cancel(turn.speaker(), sessionId, turn.turnId());
        });
        return true;
    }

    void end(@NotNull ControlledConversationSession.EndReason reason) {
        Objects.requireNonNull(reason, "reason");
        ControlledConversationSession.State previous = state.getAndSet(ControlledConversationSession.State.ENDED);
        if (previous == ControlledConversationSession.State.ENDED) return;
        ActiveTurn<P> turn = activeTurn.getAndSet(null);
        if (turn != null) {
            hooks.execute(() -> {
                turn.future().complete(ControlledTurnResult.sessionEnded(
                        sessionId, turn.turnId(), "session ended: " + reason.name().toLowerCase(java.util.Locale.ROOT)));
                hooks.cancel(turn.speaker(), sessionId, turn.turnId());
            });
        }
    }

    @NotNull List<ConversationTranscriptEntry> transcript() {
        synchronized (transcript) { return List.copyOf(transcript); }
    }

    @NotNull String sharedTranscript() {
        synchronized (transcript) {
            return transcript.stream().map(entry -> entry.speakerName() + ": " + entry.text())
                    .collect(Collectors.joining("\n"));
        }
    }

    private void startTurn(ActiveTurn<P> turn, String topicOrInstruction, String turnAgenda,
                           @Nullable A audioAnchor) {
        if (!isCurrent(turn)) return;
        String prompt = buildTurnPrompt(topicOrInstruction, turnAgenda);
        PromptSessionContext promptContext = PromptSessionContext.controlled(
                sessionId, turn.turnId(), turnAgenda, options.allowAllAddonTools(), options.allowedAddonTools());
        Availability availability = hooks.availability(turn.speaker(), audioAnchor);
        if (!availability.available()) {
            finish(turn, ControlledTurnResult.rejected(sessionId, turn.turnId(),
                    Objects.requireNonNull(availability.reason()), availability.detail()));
            return;
        }
        if (!hooks.hasCapacity()) {
            finish(turn, ControlledTurnResult.rejected(sessionId, turn.turnId(),
                    ControlledTurnResult.FailureReason.CAPACITY_EXHAUSTED, "provider capacity is exhausted"));
            return;
        }
        StartResult started = hooks.start(turn.speaker(), prompt, promptContext, audioAnchor,
                result -> hooks.execute(() -> completeAudibly(turn, result)));
        if (started == StartResult.STARTED || !isCurrent(turn)) return;
        ControlledTurnResult.FailureReason reason = switch (started) {
            case PROVIDER_UNAVAILABLE -> ControlledTurnResult.FailureReason.PROVIDER_UNAVAILABLE;
            case SPEAKER_UNAVAILABLE -> ControlledTurnResult.FailureReason.SPEAKER_UNAVAILABLE;
            case CAPACITY_EXHAUSTED -> ControlledTurnResult.FailureReason.CAPACITY_EXHAUSTED;
            case FAILED -> ControlledTurnResult.FailureReason.INTERNAL_FAILURE;
            case STARTED -> throw new IllegalStateException("unreachable");
        };
        finish(turn, ControlledTurnResult.rejected(sessionId, turn.turnId(), reason,
                "controlled turn could not start"));
    }

    private void completeAudibly(ActiveTurn<P> turn, AmbientLineResult result) {
        if (!isCurrent(turn)) return;
        P speaker = turn.speaker();
        if (result.status() == AmbientLineResult.Status.COMPLETED) {
            if (!result.transcript().isBlank()) {
                appendTranscript(new ConversationTranscriptEntry(
                        ConversationTranscriptEntry.SpeakerKind.CITIZEN,
                        hooks.id(speaker), hooks.name(speaker), result.transcript().trim(), hooks.gameTime(speaker)));
            }
            finish(turn, ControlledTurnResult.completed(sessionId, turn.turnId(), result.transcript()));
            return;
        }
        if (result.status() == AmbientLineResult.Status.CANCELLED) {
            finish(turn, new ControlledTurnResult(sessionId, turn.turnId(), ControlledTurnResult.Status.INTERRUPTED,
                    null, "", result.detail()));
            return;
        }
        Availability availability = hooks.availability(speaker, null);
        ControlledTurnResult terminal;
        if (!availability.available() && availability.reason() == ControlledTurnResult.FailureReason.SPEAKER_UNLOADED) {
            terminal = ControlledTurnResult.failed(sessionId, turn.turnId(),
                    ControlledTurnResult.FailureReason.SPEAKER_UNLOADED, result.detail());
        } else if (hooks.playerOwnsConversation(speaker)) {
            terminal = new ControlledTurnResult(sessionId, turn.turnId(), ControlledTurnResult.Status.INTERRUPTED,
                    null, "", "player conversation preempted controlled turn");
        } else {
            terminal = ControlledTurnResult.failed(sessionId, turn.turnId(),
                    ControlledTurnResult.FailureReason.PROVIDER_FAILED, result.detail());
        }
        finish(turn, terminal);
    }

    private boolean isCurrent(ActiveTurn<P> turn) {
        return state.get() == ControlledConversationSession.State.TURN_ACTIVE
                && activeTurn.get() == turn && !turn.future().isDone();
    }

    private void finish(ActiveTurn<P> turn, ControlledTurnResult result) {
        if (!activeTurn.compareAndSet(turn, null)) return;
        if (state.get() != ControlledConversationSession.State.ENDED) {
            state.compareAndSet(ControlledConversationSession.State.TURN_ACTIVE,
                                ControlledConversationSession.State.OPEN);
        }
        turn.future().complete(result);
    }

    private CompletableFuture<ControlledTurnResult> completeOnExecutor(ControlledTurnResult result) {
        CompletableFuture<ControlledTurnResult> future = new CompletableFuture<>();
        hooks.execute(() -> future.complete(result));
        return future;
    }

    private String buildTurnPrompt(String topicOrInstruction, String turnAgenda) {
        String history = sharedTranscript();
        String boundedTopic = topicOrInstruction.length() > 2_000 ? topicOrInstruction.substring(0, 2_000) : topicOrInstruction;
        return """
                ## CONTROLLED ADDON CONVERSATION
                You have explicitly been given the floor. Speak exactly one natural turn, then stop and wait.
                Meeting/session agenda: %s
                Requested topic/instruction for your turn: %s
                Shared transcript so far:
                %s
                Do not invent statements for other attendees and do not decide who speaks next.
                """.formatted(turnAgenda, boundedTopic, history.isBlank() ? "(none yet)" : history);
    }

    private void appendTranscript(ConversationTranscriptEntry entry) {
        synchronized (transcript) {
            int overhead = entry.speakerName().length() + 2;
            int maxTextChars = Math.max(1, MAX_TRANSCRIPT_CHARS - overhead);
            ConversationTranscriptEntry bounded = entry.text().length() <= maxTextChars ? entry
                    : new ConversationTranscriptEntry(entry.speakerKind(), entry.speakerId(), entry.speakerName(),
                    entry.text().substring(0, maxTextChars), entry.gameTimeTicks());
            int entryChars = overhead + bounded.text().length() + (transcript.isEmpty() ? 0 : 1);
            while (!transcript.isEmpty() && transcriptChars + entryChars > MAX_TRANSCRIPT_CHARS) {
                ConversationTranscriptEntry removed = transcript.removeFirst();
                transcriptChars -= removed.speakerName().length() + 2 + removed.text().length();
                if (!transcript.isEmpty()) transcriptChars -= 1;
            }
            if (!transcript.isEmpty()) transcriptChars += 1;
            transcript.addLast(bounded);
            transcriptChars += overhead + bounded.text().length();
        }
    }

    private record ActiveTurn<P>(@NotNull UUID turnId, @NotNull P speaker,
                                 @NotNull CompletableFuture<ControlledTurnResult> future) { }
}
