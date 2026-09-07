package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ControlledTurnResult;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ControlledConversationRuntimeTest {
    @Test
    void threeCitizensTakeAttributedTurnsAndShareBoundedHistory() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant a = hooks.add("Ada");
        FakeParticipant b = hooks.add("Borin");
        FakeParticipant c = hooks.add("Cora");
        var runtime = new ControlledConversationRuntime<>(List.of(a, b, c), "Food",
                ControlledConversationOptions.noAddonTools(), hooks);

        CompletableFuture<ControlledTurnResult> first = runtime.requestTurn(a, "Opening", null);
        assertEquals(1, hooks.startCalls);
        assertTrue(hooks.lastPrompt.contains("Meeting/session agenda: Food"));
        hooks.completeAudibly(AmbientLineResult.completed("We need grain."));
        assertTrue(first.join().completed());

        CompletableFuture<ControlledTurnResult> second = runtime.requestTurn(b, "Reply", null);
        assertTrue(hooks.lastPrompt.contains("Ada: We need grain."));
        hooks.completeAudibly(AmbientLineResult.completed("We can expand storage."));
        assertTrue(second.join().completed());

        runtime.setAgenda("Defenses");
        CompletableFuture<ControlledTurnResult> third = runtime.requestTurn(c, "Final view", null);
        assertTrue(hooks.lastPrompt.contains("Meeting/session agenda: Defenses"));
        assertTrue(hooks.lastPrompt.contains("Borin: We can expand storage."));
        hooks.completeAudibly(AmbientLineResult.completed("Then reinforce the gate."));
        assertTrue(third.join().completed());

        assertEquals(List.of("Ada", "Borin", "Cora"), runtime.transcript().stream()
                .map(entry -> entry.speakerName()).toList());
        assertEquals(3, hooks.startCalls, "silent attendees must not open provider sessions");
    }

    @Test
    void generationCompletionAloneDoesNotCompleteTurnBeforeAudiblePlayback() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        var runtime = runtime(hooks, speaker);

        CompletableFuture<ControlledTurnResult> turn = runtime.requestTurn(speaker, "Speak", null);
        hooks.signalGenerationCompleteOnly();
        assertFalse(turn.isDone(), "generation-only completion must not be exposed as audible completion");

        hooks.completeAudibly(AmbientLineResult.completed("Finished audio"));
        assertTrue(turn.join().completed());
        assertEquals("Finished audio", turn.join().transcript());
    }

    @Test
    void interruptionRejectsLateCallbackAndAllowsNextTurn() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        var runtime = runtime(hooks, speaker);

        CompletableFuture<ControlledTurnResult> first = runtime.requestTurn(speaker, "First", null);
        Consumer<AmbientLineResult> stale = hooks.currentCompletion;
        assertTrue(runtime.interruptTurn());
        assertEquals(ControlledTurnResult.Status.INTERRUPTED, first.join().status());
        assertEquals(1, hooks.cancelCalls);
        assertEquals(ControlledConversationSession.State.OPEN, runtime.state());

        stale.accept(AmbientLineResult.completed("stale text must be ignored"));
        assertTrue(runtime.transcript().isEmpty());

        CompletableFuture<ControlledTurnResult> second = runtime.requestTurn(speaker, "Second", null);
        hooks.completeAudibly(AmbientLineResult.completed("fresh text"));
        assertTrue(second.join().completed());
        assertEquals("fresh text", runtime.transcript().get(0).text());
    }

    @Test
    void typedRejectionsDoNotLeaveFloorStuck() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        var runtime = runtime(hooks, speaker);

        hooks.capacity = false;
        ControlledTurnResult capacity = runtime.requestTurn(speaker, "Speak", null).join();
        assertEquals(ControlledTurnResult.FailureReason.CAPACITY_EXHAUSTED, capacity.failureReason());
        assertEquals(ControlledConversationSession.State.OPEN, runtime.state());

        hooks.capacity = true;
        hooks.availability.put(speaker.id(), ControlledConversationRuntime.Availability.rejected(
                ControlledTurnResult.FailureReason.SPEAKER_UNLOADED, "unloaded"));
        ControlledTurnResult unavailable = runtime.requestTurn(speaker, "Speak", null).join();
        assertEquals(ControlledTurnResult.FailureReason.SPEAKER_UNLOADED, unavailable.failureReason());
        assertEquals(ControlledConversationSession.State.OPEN, runtime.state());
    }

    @Test
    void nonParticipantAndConcurrentFloorRequestsAreTyped() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        FakeParticipant outsider = hooks.add("Outsider");
        var runtime = new ControlledConversationRuntime<>(List.of(speaker), "Agenda",
                ControlledConversationOptions.noAddonTools(), hooks);

        assertEquals(ControlledTurnResult.FailureReason.SPEAKER_NOT_PARTICIPANT,
                runtime.requestTurn(outsider, "No", null).join().failureReason());

        CompletableFuture<ControlledTurnResult> active = runtime.requestTurn(speaker, "One", null);
        ControlledTurnResult concurrent = runtime.requestTurn(speaker, "Two", null).join();
        assertEquals(ControlledTurnResult.FailureReason.TURN_ALREADY_ACTIVE, concurrent.failureReason());
        hooks.completeAudibly(AmbientLineResult.completed("done"));
        assertTrue(active.join().completed());
    }

    @Test
    void playerPreemptionIsInterruptionAndLateProviderFailureDoesNotReopenSession() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        var runtime = runtime(hooks, speaker);

        CompletableFuture<ControlledTurnResult> turn = runtime.requestTurn(speaker, "Speak", null);
        hooks.playerOwned.put(speaker.id(), true);
        hooks.completeAudibly(AmbientLineResult.failed("preempted"));
        assertEquals(ControlledTurnResult.Status.INTERRUPTED, turn.join().status());
        assertEquals(ControlledConversationSession.State.OPEN, runtime.state());
    }

    @Test
    void endMidTurnCompletesOnceCancelsAndDropsLateAudio() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        var runtime = runtime(hooks, speaker);

        CompletableFuture<ControlledTurnResult> turn = runtime.requestTurn(speaker, "Speak", null);
        Consumer<AmbientLineResult> stale = hooks.currentCompletion;
        runtime.end(ControlledConversationSession.EndReason.CALLER_CANCELLED);
        runtime.end(ControlledConversationSession.EndReason.COMPLETED);

        assertEquals(ControlledTurnResult.Status.SESSION_ENDED, turn.join().status());
        assertEquals(1, hooks.cancelCalls);
        assertEquals(ControlledConversationSession.State.ENDED, runtime.state());
        stale.accept(AmbientLineResult.completed("late"));
        assertTrue(runtime.transcript().isEmpty());
    }

    @Test
    void terminalFutureCompletionAndExactCancellationUseTheConfiguredExecutor() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        hooks.executeImmediately = false;
        var runtime = runtime(hooks, speaker);

        CompletableFuture<ControlledTurnResult> turn = runtime.requestTurn(speaker, "Speak", null);
        assertFalse(turn.isDone());
        hooks.drainExecutor();
        assertEquals(1, hooks.startCalls);

        assertTrue(runtime.interruptTurn());
        assertFalse(turn.isDone(), "caller thread must not complete public turn futures directly");
        hooks.drainExecutor();

        ControlledTurnResult result = turn.join();
        assertEquals(ControlledTurnResult.Status.INTERRUPTED, result.status());
        assertEquals(runtime.sessionId(), hooks.lastCancelledSessionId);
        assertEquals(result.turnId(), hooks.lastCancelledTurnId);
    }

    @Test
    void immediateRejectionsAreAlsoCompletedThroughTheConfiguredExecutor() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant participant = hooks.add("Ada");
        FakeParticipant outsider = hooks.add("Outsider");
        hooks.executeImmediately = false;
        var runtime = new ControlledConversationRuntime<>(List.of(participant), "Agenda",
                ControlledConversationOptions.noAddonTools(), hooks);

        CompletableFuture<ControlledTurnResult> rejected = runtime.requestTurn(outsider, "No", null);
        assertFalse(rejected.isDone());
        hooks.drainExecutor();
        assertEquals(ControlledTurnResult.FailureReason.SPEAKER_NOT_PARTICIPANT,
                rejected.join().failureReason());
    }

    @Test
    void sessionAndTurnIdentityToolPolicyAndAnchorArePassedToBackend() {
        FakeHooks hooks = new FakeHooks();
        FakeParticipant speaker = hooks.add("Ada");
        Set<String> tools = Set.of("meetings:record_vote");
        var runtime = new ControlledConversationRuntime<>(List.of(speaker), "Agenda",
                ControlledConversationOptions.allowAddonTools(tools), hooks);
        FakeAnchor podium = new FakeAnchor("podium");

        CompletableFuture<ControlledTurnResult> turn = runtime.requestTurn(speaker, "Speak", podium);
        PromptSessionContext context = hooks.lastContext;
        assertEquals(runtime.sessionId(), context.sessionId());
        assertNotNull(context.turnId());
        assertEquals(tools, context.allowedAddonTools());
        assertFalse(context.allowAllAddonTools());
        assertEquals(podium, hooks.lastAnchor);

        hooks.completeAudibly(AmbientLineResult.completed("done"));
        assertEquals(context.turnId(), turn.join().turnId());
    }

    private static ControlledConversationRuntime<FakeParticipant, FakeAnchor> runtime(
            FakeHooks hooks, FakeParticipant participant) {
        return new ControlledConversationRuntime<>(List.of(participant), "Agenda",
                ControlledConversationOptions.noAddonTools(), hooks);
    }

    private record FakeParticipant(UUID id, String name) { }
    private record FakeAnchor(String name) { }

    private static final class FakeHooks implements ControlledConversationRuntime.Hooks<FakeParticipant, FakeAnchor> {
        private final List<FakeParticipant> participants = new ArrayList<>();
        private final Map<UUID, ControlledConversationRuntime.Availability> availability = new HashMap<>();
        private final Map<UUID, Boolean> playerOwned = new HashMap<>();
        private boolean capacity = true;
        private int startCalls;
        private int cancelCalls;
        private String lastPrompt;
        private PromptSessionContext lastContext;
        private FakeAnchor lastAnchor;
        private Consumer<AmbientLineResult> currentCompletion;
        private long clock;
        private boolean executeImmediately = true;
        private final List<Runnable> queuedTasks = new ArrayList<>();
        private UUID lastCancelledSessionId;
        private UUID lastCancelledTurnId;

        FakeParticipant add(String name) {
            FakeParticipant participant = new FakeParticipant(UUID.randomUUID(), name);
            participants.add(participant);
            availability.put(participant.id(), ControlledConversationRuntime.Availability.ok());
            return participant;
        }

        @Override
        public void execute(Runnable task) {
            if (executeImmediately) task.run();
            else queuedTasks.add(task);
        }

        @Override
        public UUID id(FakeParticipant participant) { return participant.id(); }

        @Override
        public String name(FakeParticipant participant) { return participant.name(); }

        @Override
        public long gameTime(FakeParticipant participant) { return ++clock; }

        @Override
        public ControlledConversationRuntime.Availability availability(FakeParticipant participant, FakeAnchor audioAnchor) {
            return availability.get(participant.id());
        }

        @Override
        public boolean hasCapacity() { return capacity; }

        @Override
        public ControlledConversationRuntime.StartResult start(
                FakeParticipant participant,
                String prompt,
                PromptSessionContext promptContext,
                FakeAnchor audioAnchor,
                Consumer<AmbientLineResult> audibleCompletion
        ) {
            startCalls++;
            lastPrompt = prompt;
            lastContext = promptContext;
            lastAnchor = audioAnchor;
            currentCompletion = audibleCompletion;
            return ControlledConversationRuntime.StartResult.STARTED;
        }

        @Override
        public void cancel(FakeParticipant participant, UUID sessionId, UUID turnId) {
            cancelCalls++;
            lastCancelledSessionId = sessionId;
            lastCancelledTurnId = turnId;
        }

        @Override
        public boolean playerOwnsConversation(FakeParticipant participant) {
            return playerOwned.getOrDefault(participant.id(), false);
        }

        void drainExecutor() {
            while (!queuedTasks.isEmpty()) {
                List<Runnable> batch = new ArrayList<>(queuedTasks);
                queuedTasks.clear();
                batch.forEach(Runnable::run);
            }
        }

        void signalGenerationCompleteOnly() {
            // Intentionally no audible callback: generation finished while fake audio is still playing.
        }

        void completeAudibly(AmbientLineResult result) {
            assertNotNull(currentCompletion, "turn must have started");
            Consumer<AmbientLineResult> callback = currentCompletion;
            currentCompletion = null;
            callback.accept(result);
        }
    }
}
