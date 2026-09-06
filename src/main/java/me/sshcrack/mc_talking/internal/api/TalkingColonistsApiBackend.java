package me.sshcrack.mc_talking.internal.api;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.CitizenActivityReservation;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationHandle;
import me.sshcrack.mc_talking.api.conversation.CitizenSpeechPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenUrgencyModifier;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ConversationTranscriptEntry;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.ConversationEligibility;
import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleListener;
import me.sshcrack.mc_talking.api.conversation.ConversationStartResult;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.api.pregen.PregenerationPromptModifier;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptContributor;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.tool.AiTool;
import me.sshcrack.mc_talking.conversations.CitizenConversation;
import me.sshcrack.mc_talking.conversations.memory.MemorySnapshotFactory;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.manager.DefaultCitizenPromptProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Full-mod implementation of the standalone addon API bridge. */
public final class TalkingColonistsApiBackend implements TalkingColonistsApi.Services {
    public static final TalkingColonistsApiBackend INSTANCE = new TalkingColonistsApiBackend();

    private static final int MAX_CONTROLLED_TRANSCRIPT_CHARS = 8_000;
    private static final Duration MAX_ADDON_ACTIVITY_LEASE = Duration.ofHours(1);
    private static final Pattern OWNER_ID = Pattern.compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9_]{0,31}");
    private static final CitizenPromptProvider DEFAULT_PROMPT_PROVIDER = new DefaultCitizenPromptProvider();

    private TalkingColonistsApiBackend() {
    }

    @Override
    public int apiMajorVersion() {
        return TalkingColonistsApi.API_MAJOR_VERSION;
    }

    public @NotNull CitizenPromptProvider defaultPromptProvider() {
        return DEFAULT_PROMPT_PROVIDER;
    }

    @Override
    public @NotNull AddonRegistration registerPromptProvider(
            @NotNull String id,
            int priority,
            @NotNull CitizenPromptProvider provider
    ) {
        return PromptRuntime.registerProvider(id, priority, provider);
    }

    @Override
    public @NotNull AddonRegistration registerPromptContributor(
            @NotNull String id,
            int order,
            @NotNull CitizenPromptContributor contributor
    ) {
        return PromptRuntime.registerContributor(id, order, contributor);
    }

    @Override
    public @NotNull AddonRegistration registerSpeechPolicy(
            @NotNull String id,
            int order,
            @NotNull CitizenSpeechPolicy policy
    ) {
        return ConversationRuleRuntime.registerSpeechPolicy(id, order, policy);
    }

    @Override
    public @NotNull AddonRegistration registerUrgencyModifier(
            @NotNull String id,
            int order,
            @NotNull CitizenUrgencyModifier modifier
    ) {
        return ConversationRuleRuntime.registerUrgencyModifier(id, order, modifier);
    }

    @Override
    public @NotNull AddonRegistration registerPregenerationPromptModifier(
            @NotNull String id,
            int order,
            @NotNull PregenerationPromptModifier modifier
    ) {
        return PregenerationPromptRuntime.register(id, order, modifier);
    }

    @Override
    public @NotNull AddonRegistration registerAiTool(
            @NotNull String namespace,
            @NotNull String name,
            @NotNull AiTool tool
    ) {
        return AiToolRuntime.register(namespace, name, tool);
    }

    @Override
    public @NotNull CitizenPromptView snapshotCitizenContext(
            @NotNull AbstractEntityCitizen citizen,
            @Nullable ServerPlayer speakingPlayer
    ) {
        var data = citizen.getCitizenData();
        if (data == null) throw new IllegalStateException("Citizen data is not available");
        return CitizenPromptViewFactory.create(data, Map.of(), speakingPlayer);
    }

    @Override
    public boolean isBusy(@NotNull AbstractEntityCitizen citizen) {
        return ConversationManager.isCitizenBusy(citizen);
    }

    @Override
    public @NotNull ConversationEligibility conversationEligibility(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull ConversationKind kind
    ) {
        return ConversationManager.conversationEligibility(citizen, kind);
    }

    @Override
    public @NotNull ConversationStartResult startPlayerConversation(
            @NotNull ServerPlayer player,
            @NotNull AbstractEntityCitizen citizen
    ) {
        return ConversationManager.startPlayerConversationDetailed(player, citizen);
    }

    @Override
    public @NotNull CompletableFuture<AmbientLineResult> requestAmbientLine(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull String promptDirective
    ) {
        if (!McTalkingConfig.hasGeminiApiKey()) {
            return CompletableFuture.completedFuture(AmbientLineResult.rejected(
                    AmbientLineResult.RejectionReason.PROVIDER_UNAVAILABLE,
                    "Gemini API key/provider is unavailable"
            ));
        }

        ConversationEligibility eligibility = ConversationManager.conversationEligibility(
                citizen, ConversationKind.ADDON_AMBIENT
        );
        if (!eligibility.eligible()) {
            return CompletableFuture.completedFuture(AmbientLineResult.rejected(
                    mapAmbientRejection(eligibility.status()),
                    eligibility.detail()
            ));
        }
        if (!ConversationManager.hasLowPriorityCapacity(1)) {
            return CompletableFuture.completedFuture(AmbientLineResult.rejected(
                    AmbientLineResult.RejectionReason.CAPACITY_EXHAUSTED,
                    "no low-priority foreground capacity is available"
            ));
        }

        CompletableFuture<AmbientLineResult> future = new CompletableFuture<>();
        boolean started = ConversationManager.startAddonAmbientSession(citizen, promptDirective, future::complete);
        if (!started) {
            future.complete(AmbientLineResult.rejected(
                    AmbientLineResult.RejectionReason.CAPACITY_EXHAUSTED,
                    "conversation became unavailable before the ambient line could start"
            ));
        }
        return future;
    }

    @Override
    public @NotNull AddonRegistration registerConversationLifecycleListener(
            @NotNull String id,
            int order,
            @NotNull ConversationLifecycleListener listener
    ) {
        return ConversationEventRuntime.register(id, order, listener);
    }

    @Override
    public @NotNull Optional<ConversationKind> activeConversationKind(@NotNull AbstractEntityCitizen citizen) {
        return Optional.ofNullable(ConversationManager.getActiveConversationKind(citizen.getUUID()));
    }

    @Override
    public @NotNull Optional<UUID> activePlayerId(@NotNull AbstractEntityCitizen citizen) {
        return Optional.ofNullable(ConversationManager.getPlayerForEntity(citizen.getUUID()));
    }

    @Override
    public boolean isPlayerInConversation(@NotNull ServerPlayer player) {
        return ConversationManager.isPlayerInConversation(player.getUUID());
    }

    @Override
    public boolean hasAmbientCapacity(int slotsNeeded) {
        if (slotsNeeded < 1) throw new IllegalArgumentException("slotsNeeded must be positive");
        return ConversationManager.hasLowPriorityCapacity(slotsNeeded);
    }

    @Override
    public boolean hasPlayerNearby(@NotNull AbstractEntityCitizen citizen, double range) {
        if (!Double.isFinite(range) || range < 0.0) {
            throw new IllegalArgumentException("range must be finite and non-negative");
        }
        MinecraftServer server = citizen.level().getServer();
        return server != null && ConversationManager.hasPlayerNearby(citizen, server, range);
    }

    @Override
    public boolean requestGracefulEnd(@NotNull AbstractEntityCitizen citizen) {
        return ConversationManager.requestGracefulEnd(citizen);
    }

    @Override
    public @NotNull Optional<CitizenActivityReservation> reserveActivity(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull String ownerId,
            @NotNull Duration timeout
    ) {
        java.util.Objects.requireNonNull(citizen, "citizen");
        java.util.Objects.requireNonNull(ownerId, "ownerId");
        if (!OWNER_ID.matcher(ownerId).matches()) {
            throw new IllegalArgumentException("ownerId must be namespaced and match " + OWNER_ID.pattern());
        }
        long timeoutNanos = validateActivityTimeout(timeout);
        UUID token = UUID.randomUUID();
        if (!ConversationManager.claimAddonActivity(citizen, token, timeoutNanos)) return Optional.empty();
        return Optional.of(new ActivityReservation(citizen.getUUID(), ownerId, token));
    }

    @Override
    public void resetAutomaticCooldown(@NotNull AbstractEntityCitizen citizen) {
        ConversationManager.forceRemoveCooldown(citizen);
    }

    @Override
    public @NotNull ControlledConversationSession createControlledSession(
            @NotNull MinecraftServer server,
            @NotNull List<AbstractEntityCitizen> participants,
            @NotNull String agenda
    ) {
        return new ControlledSession(server, participants, agenda);
    }

    @Override
    public @NotNull CitizenConversationHandle createPairConversation(
            @NotNull MinecraftServer server,
            @NotNull AbstractEntityCitizen first,
            @NotNull AbstractEntityCitizen second
    ) {
        if (first == second || first.getUUID().equals(second.getUUID())) {
            throw new IllegalArgumentException("A pair conversation requires two distinct citizens");
        }
        return new PairHandle(server, new CitizenConversation(server, List.of(first, second)));
    }

    @Override
    public boolean addMemoryEvent(@NotNull ICitizenData citizen, @NotNull String event) {
        if (event.isBlank()) throw new IllegalArgumentException("event must not be blank");
        CitizenMemories memories = getOrCreate(citizen);
        if (memories == null) return false;
        memories.addEvent(event);
        return true;
    }

    @Override
    public boolean removeMemoryEvent(@NotNull ICitizenData citizen, @NotNull String event) {
        if (event.isBlank()) throw new IllegalArgumentException("event must not be blank");
        CitizenMemories memories = getExisting(citizen);
        return memories != null && memories.removeEvent(event);
    }

    @Override
    public boolean addMemoryFact(@NotNull ICitizenData citizen, @NotNull String fact) {
        if (fact.isBlank()) throw new IllegalArgumentException("fact must not be blank");
        CitizenMemories memories = getOrCreate(citizen);
        if (memories == null) return false;
        memories.addFact(fact);
        return true;
    }

    @Override
    public boolean removeMemoryFact(@NotNull ICitizenData citizen, @NotNull String fact) {
        if (fact.isBlank()) throw new IllegalArgumentException("fact must not be blank");
        CitizenMemories memories = getExisting(citizen);
        return memories != null && memories.removeFact(fact);
    }

    @Override
    public boolean addMemoryRelationshipChange(
            @NotNull ICitizenData citizen,
            @NotNull UUID targetId,
            @NotNull CitizenRelationshipDimension dimension,
            float delta
    ) {
        java.util.Objects.requireNonNull(targetId, "targetId");
        java.util.Objects.requireNonNull(dimension, "dimension");
        if (!Float.isFinite(delta) || delta < -1.0f || delta > 1.0f) {
            throw new IllegalArgumentException("relationship delta must be finite and within [-1, 1]");
        }
        CitizenMemories memories = getOrCreate(citizen);
        if (memories == null) return false;
        memories.addRelationshipChange(targetId, dimension, delta);
        return true;
    }

    @Override
    public @NotNull Optional<CitizenMemorySnapshot> memorySnapshot(@NotNull ICitizenData citizen) {
        CitizenMemories memories = getExisting(citizen);
        if (memories == null) return Optional.empty();
        return Optional.of(MemorySnapshotFactory.create(memories));
    }

    private static long validateActivityTimeout(Duration timeout) {
        java.util.Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("activity lease timeout must be positive");
        }
        if (timeout.compareTo(MAX_ADDON_ACTIVITY_LEASE) > 0) {
            throw new IllegalArgumentException("activity lease timeout must not exceed " + MAX_ADDON_ACTIVITY_LEASE);
        }
        return timeout.toNanos();
    }

    private static AmbientLineResult.RejectionReason mapAmbientRejection(ConversationEligibility.Status status) {
        return switch (status) {
            case SLEEPING -> AmbientLineResult.RejectionReason.SLEEPING;
            case VISITOR -> AmbientLineResult.RejectionReason.VISITOR;
            case COOLDOWN -> AmbientLineResult.RejectionReason.COOLDOWN;
            case BUSY -> AmbientLineResult.RejectionReason.BUSY;
            case ADDON_POLICY_VETO -> AmbientLineResult.RejectionReason.ADDON_POLICY_VETO;
            case ELIGIBLE -> throw new IllegalArgumentException("ELIGIBLE is not a rejection");
        };
    }

    private static CitizenMemories getExisting(ICitizenData citizen) {
        if (!(citizen instanceof CitizenDataMemoryExtended extended)) return null;
        return extended.mc_talking$getMemory();
    }

    private static CitizenMemories getOrCreate(ICitizenData citizen) {
        if (!(citizen instanceof CitizenDataMemoryExtended extended)) return null;
        return extended.mc_talking$getOrInitializeMemory();
    }

    private static final class ActivityReservation implements CitizenActivityReservation {
        private final UUID citizenId;
        private final String ownerId;
        private final UUID token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private ActivityReservation(UUID citizenId, String ownerId, UUID token) {
            this.citizenId = citizenId;
            this.ownerId = ownerId;
            this.token = token;
        }

        @Override
        public @NotNull UUID citizenId() {
            return citizenId;
        }

        @Override
        public @NotNull String ownerId() {
            return ownerId;
        }

        @Override
        public boolean isClosed() {
            if (closed.get()) return true;
            if (ConversationManager.isAddonActivityActive(citizenId, token)) return false;
            closed.set(true);
            return true;
        }

        @Override
        public boolean renew(@NotNull Duration timeout) {
            if (closed.get()) return false;
            boolean renewed = ConversationManager.renewAddonActivity(
                    citizenId, token, validateActivityTimeout(timeout)
            );
            if (!renewed) closed.set(true);
            return renewed;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            ConversationManager.releaseAddonActivity(citizenId, token);
        }
    }

    private static final class ControlledSession implements ControlledConversationSession {
        private final MinecraftServer server;
        private final List<AbstractEntityCitizen> participants;
        private final Map<UUID, AbstractEntityCitizen> byId;
        private final AtomicReference<State> state = new AtomicReference<>(State.OPEN);
        private final AtomicReference<AbstractEntityCitizen> activeSpeaker = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<AmbientLineResult>> activeFuture = new AtomicReference<>();
        private final ArrayDeque<ConversationTranscriptEntry> transcript = new ArrayDeque<>();
        private int transcriptChars;
        private volatile String agenda;

        private ControlledSession(MinecraftServer server, List<AbstractEntityCitizen> participants, String agenda) {
            if (participants.isEmpty()) throw new IllegalArgumentException("Controlled session requires participants");
            this.server = java.util.Objects.requireNonNull(server, "server");
            this.agenda = java.util.Objects.requireNonNull(agenda, "agenda");
            LinkedHashMap<UUID, AbstractEntityCitizen> unique = new LinkedHashMap<>();
            for (AbstractEntityCitizen participant : participants) {
                if (participant == null) throw new IllegalArgumentException("participant must not be null");
                if (unique.putIfAbsent(participant.getUUID(), participant) != null) {
                    throw new IllegalArgumentException("Duplicate participant " + participant.getUUID());
                }
            }
            this.byId = Map.copyOf(unique);
            this.participants = List.copyOf(unique.values());
        }

        @Override
        public @NotNull List<AbstractEntityCitizen> participants() {
            return participants;
        }

        @Override
        public @NotNull State state() {
            return state.get();
        }

        @Override
        public void setAgenda(@NotNull String agenda) {
            if (state.get() == State.ENDED) throw new IllegalStateException("session ended");
            this.agenda = java.util.Objects.requireNonNull(agenda, "agenda");
        }

        @Override
        public void addPlayerStatement(@NotNull ServerPlayer player, @NotNull String statement) {
            java.util.Objects.requireNonNull(player, "player");
            if (statement.isBlank()) return;
            appendTranscript(new ConversationTranscriptEntry(
                    ConversationTranscriptEntry.SpeakerKind.PLAYER,
                    player.getUUID(),
                    player.getName().getString(),
                    statement.trim(),
                    player.level().getGameTime()
            ));
        }

        @Override
        public @NotNull CompletableFuture<AmbientLineResult> requestTurn(
                @NotNull AbstractEntityCitizen speaker,
                @NotNull String topicOrInstruction
        ) {
            java.util.Objects.requireNonNull(speaker, "speaker");
            java.util.Objects.requireNonNull(topicOrInstruction, "topicOrInstruction");
            if (!byId.containsKey(speaker.getUUID())) {
                return CompletableFuture.completedFuture(AmbientLineResult.failed("speaker is not a session participant"));
            }
            if (!state.compareAndSet(State.OPEN, State.TURN_ACTIVE)) {
                return CompletableFuture.completedFuture(AmbientLineResult.failed(
                        state.get() == State.ENDED ? "session has ended" : "another speaker already has the floor"));
            }

            CompletableFuture<AmbientLineResult> future = new CompletableFuture<>();
            activeSpeaker.set(speaker);
            activeFuture.set(future);
            String turnAgenda = agenda;
            String prompt = buildTurnPrompt(topicOrInstruction, turnAgenda);
            PromptSessionContext promptSessionContext = PromptSessionContext.withAgenda(turnAgenda);

            server.execute(() -> {
                if (state.get() != State.TURN_ACTIVE || activeSpeaker.get() != speaker) {
                    future.complete(AmbientLineResult.cancelled());
                    return;
                }
                boolean started = ConversationManager.startAddonAmbientSession(speaker, prompt, result -> {
                    if (result.status() == AmbientLineResult.Status.COMPLETED && !result.transcript().isBlank()) {
                        appendTranscript(new ConversationTranscriptEntry(
                                ConversationTranscriptEntry.SpeakerKind.CITIZEN,
                                speaker.getUUID(),
                                speaker.getName().getString(),
                                result.transcript().trim(),
                                speaker.level().getGameTime()
                        ));
                    }
                    activeSpeaker.compareAndSet(speaker, null);
                    activeFuture.compareAndSet(future, null);
                    state.compareAndSet(State.TURN_ACTIVE, State.OPEN);
                    future.complete(result);
                }, promptSessionContext);
                if (!started) {
                    activeSpeaker.compareAndSet(speaker, null);
                    activeFuture.compareAndSet(future, null);
                    state.compareAndSet(State.TURN_ACTIVE, State.OPEN);
                    future.complete(AmbientLineResult.failed("speaker is unavailable or provider capacity is exhausted"));
                }
            });
            return future;
        }

        @Override
        public boolean interruptTurn() {
            if (state.get() != State.TURN_ACTIVE) return false;
            AbstractEntityCitizen speaker = activeSpeaker.getAndSet(null);
            CompletableFuture<AmbientLineResult> future = activeFuture.getAndSet(null);
            if (!state.compareAndSet(State.TURN_ACTIVE, State.OPEN)) return false;
            if (future != null) future.complete(AmbientLineResult.cancelled());
            if (speaker != null) server.execute(() -> ConversationManager.cancelAddonAmbientSession(speaker));
            return true;
        }

        @Override
        public void end() {
            State previous = state.getAndSet(State.ENDED);
            if (previous == State.ENDED) return;
            AbstractEntityCitizen speaker = activeSpeaker.getAndSet(null);
            CompletableFuture<AmbientLineResult> future = activeFuture.getAndSet(null);
            if (future != null) future.complete(AmbientLineResult.cancelled());
            if (speaker != null) server.execute(() -> ConversationManager.cancelAddonAmbientSession(speaker));
        }

        @Override
        public @NotNull List<ConversationTranscriptEntry> transcript() {
            synchronized (transcript) {
                return List.copyOf(transcript);
            }
        }

        @Override
        public @NotNull String sharedTranscript() {
            synchronized (transcript) {
                return transcript.stream()
                        .map(entry -> entry.speakerName() + ": " + entry.text())
                        .collect(java.util.stream.Collectors.joining("\n"));
            }
        }

        private String buildTurnPrompt(String topicOrInstruction, String turnAgenda) {
            String history = sharedTranscript();
            String boundedTopic = topicOrInstruction.length() > 2_000
                    ? topicOrInstruction.substring(0, 2_000)
                    : topicOrInstruction;
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
                int maxTextChars = Math.max(1, MAX_CONTROLLED_TRANSCRIPT_CHARS - overhead);
                ConversationTranscriptEntry bounded = entry.text().length() <= maxTextChars
                        ? entry
                        : new ConversationTranscriptEntry(
                                entry.speakerKind(),
                                entry.speakerId(),
                                entry.speakerName(),
                                entry.text().substring(0, maxTextChars),
                                entry.gameTimeTicks()
                        );
                int entryChars = overhead + bounded.text().length() + (transcript.isEmpty() ? 0 : 1);
                while (!transcript.isEmpty() && transcriptChars + entryChars > MAX_CONTROLLED_TRANSCRIPT_CHARS) {
                    ConversationTranscriptEntry removed = transcript.removeFirst();
                    transcriptChars -= removed.speakerName().length() + 2 + removed.text().length();
                    if (!transcript.isEmpty()) transcriptChars -= 1;
                }
                if (!transcript.isEmpty()) transcriptChars += 1;
                transcript.addLast(bounded);
                transcriptChars += overhead + bounded.text().length();
            }
        }
    }

    private static final class PairHandle implements CitizenConversationHandle {
        private final MinecraftServer server;
        private final CitizenConversation delegate;
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<State> state = new AtomicReference<>(State.READY);
        private volatile Consumer<State> listener = ignored -> { };

        private PairHandle(MinecraftServer server, CitizenConversation delegate) {
            this.server = server;
            this.delegate = delegate;
            delegate.setOnStateChanged(coreState -> {
                State mapped = switch (coreState) {
                    case GENERATING -> State.GENERATING;
                    case PLAYING_AUDIO -> State.PLAYING_AUDIO;
                    case ENDED -> State.ENDED;
                };
                state.set(mapped);
                server.execute(() -> listener.accept(mapped));
            });
        }

        @Override
        public void start() {
            if (!started.compareAndSet(false, true) || cancelled.get()) return;
            server.execute(delegate::performConversation);
        }

        @Override
        public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            state.set(State.ENDED);
            server.execute(() -> {
                delegate.abort();
                listener.accept(State.ENDED);
            });
        }

        @Override
        public @NotNull State state() {
            return state.get();
        }

        @Override
        public void setStateListener(@NotNull Consumer<State> listener) {
            this.listener = java.util.Objects.requireNonNull(listener, "listener");
        }
    }
}
