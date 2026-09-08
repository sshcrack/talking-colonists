package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.ControlledAudioAnchor;
import me.sshcrack.mc_talking.manager.audio.ControlledTurnAudioProvider;
import me.sshcrack.mc_talking.api.conversation.ConversationEligibility;
import me.sshcrack.mc_talking.api.conversation.ConversationLifecycleEvent;
import me.sshcrack.mc_talking.api.conversation.ConversationStartResult;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import me.sshcrack.mc_talking.internal.api.ConversationEventRuntime;
import me.sshcrack.mc_talking.internal.api.ConversationRuleRuntime;
import me.sshcrack.mc_talking.internal.session.BackgroundSessionRegistry;
import me.sshcrack.mc_talking.internal.session.CitizenActivityRegistry;
import me.sshcrack.mc_talking.internal.session.ConversationCooldownRegistry;
import me.sshcrack.mc_talking.internal.session.ConversationParticipationModule;
import me.sshcrack.mc_talking.internal.session.DefaultConversationParticipationModule;
import me.sshcrack.mc_talking.internal.session.ForegroundSessionRegistry;
import me.sshcrack.mc_talking.internal.session.MinecraftConversationParticipationAdapter;
import me.sshcrack.mc_talking.internal.session.ProviderRecoveryController;
import me.sshcrack.mc_talking.handler.UrgentContactHandler;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.manager.CitizenWsClient;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.CitizenEntityAudioProvider;
import me.sshcrack.mc_talking.util.BackgroundSlotType;
import me.sshcrack.mc_talking.util.CitizenNeedAssessor;
import me.sshcrack.mc_talking.util.MumblingTopicHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/*? if neoforge {*/
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
/*? }*/
/*? if forge {*/
/*import net.minecraft.nbt.CompoundTag;
 *//*? }*/

/**
 * Manages conversations between players and citizens, including tracking
 * active conversations, entity focus, and handling conversation lifecycle.
 *
 * <h2>Slot priority</h2>
 * <ul>
 *   <li><b>High-priority (player conversations)</b>: evict the oldest
 *       <em>non-player</em> session when needed, but never kick another player
 *       conversation. If every slot belongs to a player the request is rejected.</li>
 *   <li><b>Low-priority (mumbles, citizen-to-citizen)</b>: rejected when no
 *       free or evictable non-player slot is available, so they can never
 *       displace a player.</li>
 * </ul>
 *
 * <h2>"Already busy" guard</h2>
 * Every entry point checks {@link #isCitizenBusy} before creating a new
 * session, so a citizen that is already mumbling, in a player conversation, or
 * in a citizen-to-citizen conversation will never be started again.
 */
public class ConversationManager {
    private ConversationManager() { /* utility class */ }

    private static final long ORPHAN_FOREGROUND_SLOT_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final long PREGEN_BACKGROUND_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(3);
    private static final long COMPACTION_BACKGROUND_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(10);

    /** Citizen sessions, busy leases, cooldowns, and background work each have one token-owned registry. */
    private static final ForegroundSessionRegistry<AbstractEntityCitizen, GeminiWsClient> foregroundSessions =
            new ForegroundSessionRegistry<>(
                    () -> McTalkingConfig.INSTANCE.instance().maxConcurrentAgents,
                    System::nanoTime,
                    GeminiWsClient::close,
                    GeminiWsClient::isLifecycleClosed,
                    ConversationManager::onForegroundTerminated
            );
    private static final ConversationParticipationModule conversationParticipation =
            new DefaultConversationParticipationModule(citizenId -> foregroundSessions.snapshot(citizenId)
                    .map(snapshot -> new ConversationParticipationModule.Ownership(
                            snapshot.token(), snapshot.playerId()))
                    .orElse(null));
    private static final Map<UUID, MinecraftConversationParticipationAdapter> participationAdapters =
            new ConcurrentHashMap<>();
    private static final CitizenActivityRegistry activities = new CitizenActivityRegistry(System::nanoTime);
    private static final ConversationCooldownRegistry cooldowns = new ConversationCooldownRegistry(System::currentTimeMillis);
    private static final BackgroundSessionRegistry<GeminiLiveClient> backgroundSessions =
            new BackgroundSessionRegistry<>(
                    () -> McTalkingConfig.INSTANCE.instance().maxConcurrentBackground,
                    System::nanoTime,
                    type -> type == BackgroundSlotType.COMPACTION
                            ? COMPACTION_BACKGROUND_TIMEOUT_NANOS
                            : PREGEN_BACKGROUND_TIMEOUT_NANOS,
                    GeminiLiveClient::close,
                    GeminiLiveClient::isClosed
            );


    /** Exact-ownership handle for internal non-WebSocket citizen activity. */
    public static final class CoreActivityReservation implements AutoCloseable {
        private final CitizenActivityRegistry.Token token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private CoreActivityReservation(CitizenActivityRegistry.Token token) {
            this.token = token;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            activities.release(token.citizenId(), token.ownershipId(), token.ownerKind());
        }
    }

    /** Exact token-owned foreground capacity/client reservation used by core conversation flows. */
    public static final class ForegroundReservation implements AutoCloseable {
        private final ForegroundSessionRegistry.Token token;
        private final MinecraftConversationParticipationAdapter participation;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean lifecycleStarted = new AtomicBoolean(false);

        private ForegroundReservation(
                ForegroundSessionRegistry.Token token,
                MinecraftConversationParticipationAdapter participation
        ) {
            this.token = token;
            this.participation = participation;
        }

        public UUID citizenId() {
            return token.citizenId();
        }

        public boolean attachClient(GeminiWsClient client) {
            // Bind ownership before any rejection path can close the client. A late rejected close
            // must not fall back to an unowned NONE write that could erase a replacement session.
            client.bindConversationParticipation(participation);
            if (closed.get()) {
                client.close();
                return false;
            }
            if (!foregroundSessions.attachClient(token, client)) return false;
            client.addRecoveryObserver(diagnostic -> {
                if (!lifecycleStarted.get() || !foregroundSessions.isCurrent(token)) return;
                if (diagnostic.state() == ProviderRecoveryController.State.RECOVERING) {
                    foregroundSessions.markRecovering(token, diagnostic.totalRecoveryAttempts(), diagnostic.detail());
                } else if (diagnostic.state() == ProviderRecoveryController.State.ACTIVE) {
                    foregroundSessions.markActive(token, diagnostic.detail());
                }
            });
            return true;
        }

        public boolean activate() {
            if (closed.get() || !foregroundSessions.markActive(token, "conversation active")) return false;
            if (lifecycleStarted.compareAndSet(false, true)) {
                foregroundSessions.snapshot(token.citizenId()).ifPresent(snapshot ->
                        dispatchLifecycleStarted(snapshot.entity(), snapshot.kind(), snapshot.playerId(), snapshot.sessionId(), snapshot.turnId()));
            }
            return true;
        }

        public boolean isCurrent() {
            return !closed.get() && foregroundSessions.isCurrent(token);
        }

        public void markUrgentWalking(boolean active) {
            participation.urgentWalking(active);
        }

        public boolean end(ForegroundSessionRegistry.TerminalReason reason, String detail) {
            closed.set(true);
            return foregroundSessions.end(token, reason, detail);
        }

        @Override
        public void close() {
            end(ForegroundSessionRegistry.TerminalReason.CANCELLED, "foreground reservation closed");
        }
    }

    /** Ownership handle for one bounded background task. */
    public static final class BackgroundReservation implements AutoCloseable {
        private final BackgroundSessionRegistry.Token token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private BackgroundReservation(BackgroundSessionRegistry.Token token) {
            this.token = token;
        }

        public boolean attachClient(GeminiLiveClient client) {
            if (closed.get()) {
                closeQuietly(client, "late background client", token.citizenId());
                return false;
            }
            return backgroundSessions.attach(token, client);
        }

        public boolean isActive() {
            return !closed.get() && backgroundSessions.isActive(token);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            backgroundSessions.release(token);
        }
    }

    // -------------------------------------------------------------------------
    // Token-owned foreground/background reservations
    // -------------------------------------------------------------------------

    /** Reserves one low-priority foreground session. Ambient work never evicts active work. */
    public static ForegroundReservation reserveAmbientForeground(
            AbstractEntityCitizen citizen,
            ConversationKind kind
    ) {
        return reserveAmbientForeground(citizen, kind, null, null);
    }

    private static ForegroundReservation reserveAmbientForeground(
            AbstractEntityCitizen citizen,
            ConversationKind kind,
            @Nullable UUID sessionId,
            @Nullable UUID turnId
    ) {
        java.util.Objects.requireNonNull(citizen, "citizen");
        java.util.Objects.requireNonNull(kind, "kind");
        UUID citizenId = citizen.getUUID();
        backgroundSessions.cancel(citizenId);
        if (activities.isBusy(citizenId)) return null;
        var reservation = foregroundSessions.reserve(
                citizenId, citizen, kind, ForegroundSessionRegistry.Priority.AMBIENT, null, sessionId, turnId);
        if (!reservation.granted()) return null;
        McTalking.LOGGER.info("[ConversationManager] Reserved ambient foreground session for {} ({})", citizenId, kind);
        return new ForegroundReservation(reservation.token(), registerParticipation(citizen, reservation.token()));
    }

    private static ForegroundReservation reservePlayerForeground(
            AbstractEntityCitizen citizen,
            UUID playerId
    ) {
        UUID citizenId = citizen.getUUID();
        backgroundSessions.cancel(citizenId);
        var reservation = foregroundSessions.reserve(
                citizenId, citizen, ConversationKind.PLAYER, ForegroundSessionRegistry.Priority.PLAYER, playerId);
        if (!reservation.granted()) return null;
        McTalking.LOGGER.info("[ConversationManager] Reserved player foreground session for {} / {}", citizenId, playerId);
        return new ForegroundReservation(reservation.token(), registerParticipation(citizen, reservation.token()));
    }

    private static MinecraftConversationParticipationAdapter registerParticipation(
            AbstractEntityCitizen citizen,
            ForegroundSessionRegistry.Token token
    ) {
        conversationParticipation.register(token);
        var adapter = new MinecraftConversationParticipationAdapter(citizen, token, conversationParticipation);
        participationAdapters.put(token.ownershipId(), adapter);
        return adapter;
    }

    public static boolean hasLowPriorityCapacity(int slotsNeeded) {
        return foregroundSessions.hasLowPriorityCapacity(slotsNeeded);
    }

    public static boolean hasFreeCapacity(int slotsNeeded) {
        return foregroundSessions.hasLowPriorityCapacity(slotsNeeded);
    }

    public static boolean hasFreeBackgroundCapacity(int slotsNeeded) {
        backgroundSessions.purge();
        return backgroundSessions.hasCapacity(slotsNeeded);
    }

    public static int getUsedBackgroundSlots() {
        backgroundSessions.purge();
        return backgroundSessions.size();
    }

    public static int getMaxBackgroundSlots() {
        return McTalkingConfig.INSTANCE.instance().maxConcurrentBackground;
    }

    public static BackgroundReservation reserveBackgroundSlot(
            AbstractEntityCitizen citizen,
            BackgroundSlotType type
    ) {
        var token = backgroundSessions.reserve(citizen.getUUID(), type);
        if (token == null) return null;
        McTalking.LOGGER.info("[ConversationManager] Reserved background slot for {} (type={})", citizen.getUUID(), type);
        return new BackgroundReservation(token);
    }

    public static boolean cancelBackgroundSlot(UUID entityId, String reason) {
        boolean cancelled = backgroundSessions.cancel(entityId);
        if (cancelled) {
            McTalking.LOGGER.info("[ConversationManager] Released background slot for {} ({})", entityId, reason);
        }
        return cancelled;
    }

    /** Runs bounded lifecycle cleanup independent of new slot requests. */
    public static void tickMaintenance() {
        for (BackgroundSessionRegistry.Expired expired : backgroundSessions.purge()) {
            if (expired.deadlineExceeded()) {
                McTalking.LOGGER.warn("[ConversationManager] Background {} session for {} exceeded its deadline",
                        expired.type(), expired.citizenId());
            }
        }

        int startupTimeouts = foregroundSessions.purgeStartupTimeouts(ORPHAN_FOREGROUND_SLOT_TIMEOUT_NANOS);
        if (startupTimeouts > 0) {
            McTalking.LOGGER.warn("[ConversationManager] Recovered {} foreground startup reservation(s) after timeout", startupTimeouts);
        }

        for (var snapshot : foregroundSessions.snapshots()) {
            AbstractEntityCitizen entity = snapshot.entity();
            if (!entity.isAlive() || entity.isRemoved()) {
                foregroundSessions.end(snapshot.token(), ForegroundSessionRegistry.TerminalReason.ENTITY_UNAVAILABLE,
                        "citizen unloaded, removed, or died");
            }
        }

        for (Runnable timeoutAction : activities.purgeExpired()) {
            try {
                timeoutAction.run();
            } catch (Throwable t) {
                McTalking.LOGGER.error("[ConversationManager] Core activity timeout cleanup failed", t);
            }
        }
    }

    private static void closeQuietly(GeminiLiveClient client, String reason, UUID entityId) {
        if (client == null) return;
        try {
            client.close();
        } catch (Exception e) {
            McTalking.LOGGER.warn("[ConversationManager] Error closing background client {} ({})", entityId, reason, e);
        }
    }

    // -------------------------------------------------------------------------
    // Debug / inspection accessors
    // -------------------------------------------------------------------------

    public static Map<UUID, GeminiWsClient> getClients() {
        return foregroundSessions.clientsSnapshot();
    }

    public static Map<UUID, UUID> getCitizenToPlayer() {
        return foregroundSessions.citizenToPlayerSnapshot();
    }

    public static Map<UUID, UUID> getPlayerConversationPartners() {
        return foregroundSessions.playerToCitizenSnapshot();
    }

    public static Map<UUID, Long> getLastSessionEndTimes() {
        return cooldowns.endTimesSnapshot();
    }

    public static List<ForegroundSessionRegistry.TerminalDiagnostic> getForegroundTerminalHistory() {
        return foregroundSessions.terminalHistory();
    }

    // -------------------------------------------------------------------------
    // Busy ownership and cooldowns
    // -------------------------------------------------------------------------

    public static CoreActivityReservation reserveCoreActivity(
            AbstractEntityCitizen citizen,
            long timeout,
            TimeUnit unit,
            Runnable onTimeout,
            Runnable onPreempt
    ) {
        java.util.Objects.requireNonNull(citizen, "citizen");
        java.util.Objects.requireNonNull(unit, "unit");
        java.util.Objects.requireNonNull(onTimeout, "onTimeout");
        java.util.Objects.requireNonNull(onPreempt, "onPreempt");
        if (timeout <= 0) throw new IllegalArgumentException("timeout must be positive");

        UUID citizenId = citizen.getUUID();
        CitizenActivityRegistry.Token token = activities.reserveCore(
                citizenId, foregroundSessions.isBusy(citizenId), unit.toNanos(timeout), onTimeout, onPreempt);
        return token == null ? null : new CoreActivityReservation(token);
    }

    private static Runnable removeActivityForPlayerPreemption(UUID citizenId) {
        return activities.preemptForPlayer(citizenId);
    }

    public static boolean isCitizenBusy(AbstractEntityCitizen citizen) {
        UUID id = citizen.getUUID();
        return foregroundSessions.isBusy(id) || activities.isBusy(id);
    }

    public static boolean claimAddonActivity(AbstractEntityCitizen citizen, UUID token, long timeoutNanos) {
        return activities.reserveAddon(citizen.getUUID(), token, foregroundSessions.isBusy(citizen.getUUID()), timeoutNanos);
    }

    public static boolean renewAddonActivity(UUID citizenId, UUID token, long timeoutNanos) {
        return activities.renewAddon(citizenId, token, timeoutNanos);
    }

    public static boolean isAddonActivityActive(UUID citizenId, UUID token) {
        return activities.isActive(citizenId, token, CitizenActivityRegistry.OwnerKind.ADDON);
    }

    public static boolean releaseAddonActivity(UUID citizenId, UUID token) {
        return activities.release(citizenId, token, CitizenActivityRegistry.OwnerKind.ADDON);
    }

    public static boolean requestGracefulEnd(AbstractEntityCitizen citizen) {
        GeminiWsClient client = foregroundSessions.client(citizen.getUUID());
        if (client == null) return false;
        client.endConversationWhenPossible();
        return true;
    }

    public static void recordCooldown(AbstractEntityCitizen citizen) {
        cooldowns.record(citizen.getUUID(), computeNeedSignature(citizen));
    }

    public static boolean isCitizenOnCooldown(AbstractEntityCitizen citizen) {
        long cooldownMs = McTalkingConfig.INSTANCE.instance().citizenCooldownSeconds * 1000L;
        return cooldowns.isActive(citizen.getUUID(), computeNeedSignature(citizen), cooldownMs);
    }

    public static void forceRemoveCooldown(AbstractEntityCitizen citizen) {
        cooldowns.reset(citizen.getUUID());
    }

    public static String computeNeedSignature(AbstractEntityCitizen citizen) {
        return CitizenNeedAssessor.computeNeedSignature(citizen);
    }

    private static void dispatchLifecycleStarted(
            AbstractEntityCitizen citizen,
            ConversationKind kind,
            @Nullable UUID playerId
    ) {
        dispatchLifecycleStarted(citizen, kind, playerId, null, null);
    }

    private static void dispatchLifecycleStarted(
            AbstractEntityCitizen citizen,
            ConversationKind kind,
            @Nullable UUID playerId,
            @Nullable UUID sessionId,
            @Nullable UUID turnId
    ) {
        dispatchLifecycleEvent(new ConversationLifecycleEvent(
                ConversationLifecycleEvent.Phase.STARTED, kind, citizen, playerId, sessionId, turnId,
                citizen.level().getGameTime()));
    }

    private static void dispatchLifecycleEnded(
            AbstractEntityCitizen citizen,
            ConversationKind kind,
            @Nullable UUID playerId
    ) {
        dispatchLifecycleEnded(citizen, kind, playerId, null, null);
    }

    private static void dispatchLifecycleEnded(
            AbstractEntityCitizen citizen,
            ConversationKind kind,
            @Nullable UUID playerId,
            @Nullable UUID sessionId,
            @Nullable UUID turnId
    ) {
        dispatchLifecycleEvent(new ConversationLifecycleEvent(
                ConversationLifecycleEvent.Phase.ENDED, kind, citizen, playerId, sessionId, turnId,
                citizen.level().getGameTime()));
    }

    private static void dispatchLifecycleEvent(ConversationLifecycleEvent event) {
        MinecraftServer server = event.citizen().level().getServer();
        if (server != null && !server.isSameThread()) {
            server.execute(() -> ConversationEventRuntime.emit(event));
        } else {
            ConversationEventRuntime.emit(event);
        }
    }

    private static void onForegroundTerminated(
            ForegroundSessionRegistry.EndedSession<AbstractEntityCitizen, GeminiWsClient> ended
    ) {
        var snapshot = ended.snapshot();
        var participation = participationAdapters.remove(snapshot.token().ownershipId());
        if (participation != null) participation.complete();
        // Provider callbacks may arrive off-thread. Lifecycle dispatch, need inspection for cooldown,
        // inventory/status cleanup, and every other Minecraft-world read happen on the server thread.
        runOnServerThread(snapshot.entity(), () -> {
            boolean wasActive = ended.previousState() == ForegroundSessionRegistry.State.ACTIVE
                    || ended.previousState() == ForegroundSessionRegistry.State.RECOVERING;
            if (wasActive) {
                dispatchLifecycleEnded(snapshot.entity(), snapshot.kind(), snapshot.playerId(), snapshot.sessionId(), snapshot.turnId());
                if (snapshot.priority() == ForegroundSessionRegistry.Priority.AMBIENT
                        && snapshot.kind() != ConversationKind.CONTROLLED
                        && switch (ended.reason()) {
                            case COMPLETED, CANCELLED, PROVIDER_FAILURE, RECOVERY_EXHAUSTED -> true;
                            default -> false;
                        }) {
                    recordCooldown(snapshot.entity());
                }
            }

            if (snapshot.playerId() != null) {
                schedulePlayerPresentationCleanup(snapshot.entity(), snapshot.playerId(), false);
            }
        });
    }

    private static ForegroundSessionRegistry.TerminalReason providerTerminalReason(GeminiWsClient client) {
        return switch (client.getRecoveryDiagnostic().terminalReason()) {
            case RECOVERY_EXHAUSTED -> ForegroundSessionRegistry.TerminalReason.RECOVERY_EXHAUSTED;
            case NORMAL_CLOSE -> ForegroundSessionRegistry.TerminalReason.COMPLETED;
            case INTENTIONAL_CLOSE -> ForegroundSessionRegistry.TerminalReason.CANCELLED;
            default -> ForegroundSessionRegistry.TerminalReason.PROVIDER_FAILURE;
        };
    }

    private static void runOnServerThread(AbstractEntityCitizen citizen, Runnable action) {
        MinecraftServer server = citizen.level().getServer();
        if (server != null && !server.isSameThread()) server.execute(action);
        else action.run();
    }

    private static void schedulePlayerPresentationCleanup(
            AbstractEntityCitizen entity,
            UUID playerId,
            boolean sendMessage
    ) {
        runOnServerThread(entity, () -> {
            if (!entity.isAlive()) return;
            MinecraftServer server = entity.level().getServer();
            if (server == null) return;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return;

            for (ItemStack item : player.getInventory().items) {
                if (item.getItem() instanceof CitizenTalkingDevice) {
                    /*? if forge {*/
                    /*CompoundTag tag = item.getOrCreateTag();
                    tag.putInt("CustomModelData", 0);
                    *//*?}*/
                    /*? if neoforge {*/
                    item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
                    /*?}*/
                }
            }
            if (sendMessage) {
                player.sendSystemMessage(Component.translatable("mc_talking.too_far")
                        .withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Public conversation API
    // -------------------------------------------------------------------------

    public static boolean canCitizenSpeak(AbstractEntityCitizen citizen) {
        return canCitizenSpeak(citizen, ConversationKind.ADDON_AMBIENT);
    }

    public static boolean canCitizenSpeak(AbstractEntityCitizen citizen, boolean isPlayerRequest) {
        return canCitizenSpeak(citizen, isPlayerRequest ? ConversationKind.PLAYER : ConversationKind.ADDON_AMBIENT);
    }

    /** Detailed, side-effect-free speech eligibility used by core and addons. */
    public static ConversationEligibility conversationEligibility(
            AbstractEntityCitizen citizen,
            ConversationKind kind
    ) {
        if (citizen.isSleeping()) {
            return ConversationEligibility.rejected(ConversationEligibility.Status.SLEEPING, "citizen is sleeping");
        }
        if (citizen instanceof VisitorCitizen) {
            return ConversationEligibility.rejected(ConversationEligibility.Status.VISITOR, "visitors are not supported speakers");
        }

        if (kind != ConversationKind.PLAYER) {
            if (kind != ConversationKind.CONTROLLED && isCitizenOnCooldown(citizen)) {
                return ConversationEligibility.rejected(ConversationEligibility.Status.COOLDOWN, "automatic conversation cooldown is active");
            }
            if (isCitizenBusy(citizen)) {
                return ConversationEligibility.rejected(ConversationEligibility.Status.BUSY, "citizen is already busy");
            }
        }

        if (!ConversationRuleRuntime.addonsAllowSpeech(citizen, kind)) {
            return ConversationEligibility.rejected(
                    ConversationEligibility.Status.ADDON_POLICY_VETO,
                    "an addon speech policy vetoed this conversation kind"
            );
        }
        return ConversationEligibility.eligibleResult();
    }

    public static boolean canCitizenSpeak(AbstractEntityCitizen citizen, ConversationKind kind) {
        return conversationEligibility(citizen, kind).eligible();
    }


    /**
     * Starts a citizen mumbling to itself when a player is nearby (low-priority).
     *
     * <p>Silently returns if the citizen is already busy or if no low-priority
     * slot is available (pool is full of player conversations).</p>
     */
    public static void startMumbling(AbstractEntityCitizen citizen) {
        if (!McTalkingConfig.hasGeminiApiKey()) return;
        startLowPrioritySession(citizen, MumblingTopicHelper.buildPrompt(citizen), ConversationKind.MUMBLE);
    }

    /**
     * Starts a low-priority, one-sided AI voice session for {@code citizen}.
     *
     * <h4>How it works under the hood</h4>
     * <p>This method opens a <em>Gemini Live WebSocket</em> in system-controlled
     * (mumbling) mode.  The session uses
     * {@code CitizenPromptProvider.generateSystemControlledRoleplayPrompt(...)}
     * as the base system prompt, then appends {@code userPrompt} to that system
     * prompt before sending the first turn.  The model then speaks aloud as the
     * citizen, and the session closes automatically when talking is complete.</p>
     *
     * <h4>Prompt authoring contract — IMPORTANT</h4>
     * <p>{@code userPrompt} is injected <strong>into the system prompt</strong>, not
     * sent as a user chat message.  It must therefore be written as a directive
     * addressed to the AI model — second person, imperative — describing what the
     * citizen should say or do in this turn.  Examples of correct phrasing:</p>
     * <pre>{@code
     * // ✓ Correct — system directive style:
     * "## CURRENT TASK\nTurn to Aldric and mention the food shortage you heard about. One sentence."
     *
     * // ✗ Wrong — reads like a user chat message:
     * "Tell Aldric about this: Rumor: I heard from ... that ..."
     * }</pre>
     * <p>For a reference implementation see
     * {@link me.sshcrack.mc_talking.broadcast.BroadcastPropagationService} (broadcast
     * yelling prompt) and
     * {@link me.sshcrack.mc_talking.rumor.RumorMillService#attemptRumorTalking}.</p>
     *
     * <h4>Guards</h4>
     * <ul>
     *   <li>API key must be configured.</li>
     *   <li>{@link #canCitizenSpeak} must return {@code true} (subsumes busy,
     *       cooldown, sleeping, and visitor checks).</li>
     *   <li>A low-priority foreground reservation must be available.</li>
     * </ul>
     * <p>Silently returns without throwing if any guard fails.</p>
     *
     * @param citizen    the citizen who will speak
     * @param userPrompt a system-prompt addition written as a directive to the AI
     *                   model; see authoring contract above
     */
    public static boolean startLowPrioritySession(AbstractEntityCitizen citizen, String userPrompt) {
        return startLowPrioritySession(citizen, userPrompt, ConversationKind.ADDON_AMBIENT);
    }

    /** Supported internal entry used by the public addon conversation service. */
    public static boolean startAddonAmbientSession(AbstractEntityCitizen citizen, String userPrompt) {
        return startAddonAmbientSession(citizen, userPrompt, null);
    }

    /**
     * Starts one addon-directed ambient line. When supplied, {@code completion} is dispatched on
     * the Minecraft server thread after audible playback has drained, or when the session closes
     * terminally before completing.
     */
    public static boolean startAddonAmbientSession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            Consumer<AmbientLineResult> completion
    ) {
        return startAddonAmbientSession(citizen, userPrompt, completion, PromptSessionContext.empty());
    }

    public static boolean startAddonAmbientSession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            Consumer<AmbientLineResult> completion,
            PromptSessionContext promptSessionContext
    ) {
        return startAddonAmbientSession(citizen, userPrompt, completion, promptSessionContext, null);
    }

    public static boolean startAddonAmbientSession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            Consumer<AmbientLineResult> completion,
            PromptSessionContext promptSessionContext,
            ControlledAudioAnchor audioAnchor
    ) {
        return startLowPrioritySession(citizen, userPrompt, ConversationKind.ADDON_AMBIENT, completion,
                promptSessionContext, audioAnchor, null);
    }

    /** Starts an exact addon-controlled floor turn without automatic-conversation cooldown semantics. */
    public static boolean startControlledAmbientSession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            Consumer<AmbientLineResult> completion,
            PromptSessionContext promptSessionContext,
            ControlledAudioAnchor audioAnchor,
            int maxOutputTokens
    ) {
        if (maxOutputTokens < 0) throw new IllegalArgumentException("maxOutputTokens must not be negative");
        return startLowPrioritySession(citizen, userPrompt, ConversationKind.CONTROLLED, completion,
                promptSessionContext, audioAnchor, maxOutputTokens == 0 ? null : maxOutputTokens);
    }

    /** Cancels only the exact controlled turn identity; stale cancellation cannot kill a replacement turn. */
    public static boolean cancelControlledAmbientSession(
            AbstractEntityCitizen citizen,
            UUID sessionId,
            UUID turnId
    ) {
        UUID citizenId = citizen.getUUID();
        if (foregroundSessions.playerForCitizen(citizenId) != null) return false;
        GeminiWsClient client = foregroundSessions.client(citizenId);
        ForegroundSessionRegistry.Token token = foregroundSessions.token(citizenId);
        if (token == null || !(client instanceof CitizenWsClient cws)
                || !cws.ownsControlledTurn(sessionId, turnId)) return false;
        return foregroundSessions.end(token, ForegroundSessionRegistry.TerminalReason.CANCELLED,
                "controlled turn cancelled");
    }

    /** Cancels an addon/system ambient session without exposing its Gemini client. */
    public static boolean cancelAddonAmbientSession(AbstractEntityCitizen citizen) {
        UUID citizenId = citizen.getUUID();
        if (foregroundSessions.playerForCitizen(citizenId) != null) return false;
        GeminiWsClient client = foregroundSessions.client(citizenId);
        ForegroundSessionRegistry.Token token = foregroundSessions.token(citizenId);
        if (token == null || !(client instanceof CitizenWsClient cws) || !cws.isMumbling()) return false;
        return foregroundSessions.end(token, ForegroundSessionRegistry.TerminalReason.CANCELLED,
                "ambient session cancelled");
    }

    /**
     * Starts the one-sided urgent announcement on an already-owned urgent-contact reservation.
     *
     * <p>The caller owns the reservation lifecycle. This method attaches/activates the provider
     * and reports audible/provider completion, but deliberately does not end the reservation. That
     * lets the urgent-contact lifecycle decide completion versus cancellation and lets an in-place
     * player promotion retain the exact same foreground token.</p>
     */
    public static boolean startUrgentAnnouncement(
            ForegroundReservation reservation,
            AbstractEntityCitizen citizen,
            String originPlayerName,
            Consumer<AmbientLineResult> completion
    ) {
        java.util.Objects.requireNonNull(reservation, "reservation");
        java.util.Objects.requireNonNull(citizen, "citizen");
        java.util.Objects.requireNonNull(originPlayerName, "originPlayerName");
        java.util.Objects.requireNonNull(completion, "completion");
        if (!McTalkingConfig.hasGeminiApiKey()
                || !reservation.citizenId().equals(citizen.getUUID())
                || !reservation.isCurrent()) {
            return false;
        }

        AtomicBoolean startupCommitted = new AtomicBoolean(false);
        AtomicBoolean completionDelivered = new AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<AmbientLineResult> earlyResult =
                new java.util.concurrent.atomic.AtomicReference<>();

        Consumer<AmbientLineResult> report = result -> runOnServerThread(citizen, () -> {
            if (!startupCommitted.get()) {
                earlyResult.compareAndSet(null, result);
                return;
            }
            if (completionDelivered.compareAndSet(false, true)) completion.accept(result);
        });

        try {
            CitizenWsClient client = new CitizenWsClient(
                    new ControlledTurnAudioProvider(citizen, null),
                    citizen,
                    c -> report.accept(AmbientLineResult.completed(c.getSessionTranscriptSnapshot())),
                    PromptSessionContext.empty(),
                    null
            );

            if (!reservation.attachClient(client)) return false;

            client.addOnCloseAction(() -> {
                var diagnostic = client.getRecoveryDiagnostic();
                report.accept(client.isPlayerTakeoverPending()
                        ? AmbientLineResult.cancelled("player conversation preempted urgent announcement")
                        : AmbientLineResult.failed(diagnostic.detail()));
            });

            if (!reservation.activate()) return false;
            client.addPromptTextAfterTalkingComplete(
                    MumblingTopicHelper.buildUrgentContactPrompt(citizen, originPlayerName));

            startupCommitted.set(true);
            AmbientLineResult early = earlyResult.getAndSet(null);
            if (early != null) report.accept(early);
            return true;
        } catch (RuntimeException e) {
            McTalking.LOGGER.error("[ConversationManager] Failed to start urgent announcement for {}",
                    citizen.getUUID(), e);
            return false;
        }
    }

    private static boolean startLowPrioritySession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            ConversationKind kind
    ) {
        return startLowPrioritySession(citizen, userPrompt, kind, null, PromptSessionContext.empty(), null, null);
    }

    private static boolean startLowPrioritySession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            ConversationKind kind,
            Consumer<AmbientLineResult> completion
    ) {
        return startLowPrioritySession(citizen, userPrompt, kind, completion, PromptSessionContext.empty(), null, null);
    }

    private static boolean startLowPrioritySession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            ConversationKind kind,
            Consumer<AmbientLineResult> completion,
            PromptSessionContext promptSessionContext,
            ControlledAudioAnchor audioAnchor,
            Integer maxOutputTokens
    ) {
        if (!McTalkingConfig.hasGeminiApiKey()) return false;
        if (!canCitizenSpeak(citizen, kind)) return false;

        UUID citizenId = citizen.getUUID();
        ForegroundReservation reservation = reserveAmbientForeground(
                citizen, kind, promptSessionContext.sessionId(), promptSessionContext.turnId());
        if (reservation == null) {
            McTalking.LOGGER.debug("[ConversationManager] No low-priority slot available for session for citizen {}", citizenId);
            return false;
        }

        try {
            AtomicBoolean audibleCompletion = new AtomicBoolean(false);
            AtomicBoolean completionDelivered = new AtomicBoolean(false);
            CitizenWsClient client = new CitizenWsClient(new ControlledTurnAudioProvider(citizen, audioAnchor), citizen, c -> {
                String transcript = c.getSessionTranscriptSnapshot();
                runOnServerThread(citizen, () -> {
                    audibleCompletion.set(true);
                    if (!reservation.end(ForegroundSessionRegistry.TerminalReason.COMPLETED,
                            "ambient audible turn completed")) return;
                    if (completion != null && completionDelivered.compareAndSet(false, true)) {
                        completion.accept(AmbientLineResult.completed(transcript));
                    }
                });
            }, promptSessionContext, maxOutputTokens);

            if (!reservation.attachClient(client)) {
                reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                        "foreground ownership changed before client attach");
                return false;
            }

            client.addOnCloseAction(() -> runOnServerThread(citizen, () -> {
                var diagnostic = client.getRecoveryDiagnostic();
                reservation.end(providerTerminalReason(client), diagnostic.detail());
                if (completion != null && !audibleCompletion.get()
                        && completionDelivered.compareAndSet(false, true)) {
                    completion.accept(client.isPlayerTakeoverPending()
                            ? AmbientLineResult.cancelled("player conversation preempted controlled turn")
                            : AmbientLineResult.failed(diagnostic.detail()));
                }
            }));

            if (!reservation.activate()) {
                reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                        "foreground ownership changed before activation");
                return false;
            }
            client.addPromptTextAfterTalkingComplete(userPrompt);
            return true;
        } catch (RuntimeException e) {
            reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                    "failed to start low-priority provider session: " + e.getClass().getSimpleName());
            McTalking.LOGGER.error("[ConversationManager] Failed to start low-priority session for {}", citizenId, e);
            return false;
        }
    }

    /**
     * Returns {@code true} if any online player is within {@code range} blocks
     * of the given citizen in the same dimension.
     */
    public static boolean hasPlayerNearby(AbstractEntityCitizen citizen, MinecraftServer server, double range) {
        double rangeSqr = range * range;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == citizen.level() && player.distanceToSqr(citizen) <= rangeSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>If the citizen is already mumbling the existing session is reused
     * (no reconnect). If the citizen is in a different active session (citizen-to-
     * citizen) it is closed first so the player always wins.</p>
     */
    public static ConversationStartResult startPlayerConversationDetailed(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (!McTalkingConfig.hasGeminiApiKey()) {
            player.sendSystemMessage(Component.translatable("mc_talking.no_key").withStyle(ChatFormatting.RED));
            return ConversationStartResult.rejected(
                    ConversationStartResult.Status.PROVIDER_UNAVAILABLE,
                    "Gemini API key/provider is unavailable");
        }

        ConversationEligibility eligibility = conversationEligibility(citizen, ConversationKind.PLAYER);
        if (!eligibility.eligible()) {
            ConversationStartResult.Status status = switch (eligibility.status()) {
                case SLEEPING -> ConversationStartResult.Status.SLEEPING;
                case VISITOR -> ConversationStartResult.Status.VISITOR;
                case ADDON_POLICY_VETO -> ConversationStartResult.Status.ADDON_POLICY_VETO;
                default -> ConversationStartResult.Status.FAILED;
            };
            return ConversationStartResult.rejected(status, eligibility.detail());
        }

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        UUID existingPlayerId = foregroundSessions.playerForCitizen(citizenId);
        if (existingPlayerId != null && !existingPlayerId.equals(playerId)) {
            player.sendSystemMessage(Component.translatable("mc_talking.citizen_in_use").withStyle(ChatFormatting.YELLOW));
            return ConversationStartResult.rejected(
                    ConversationStartResult.Status.IN_USE_BY_OTHER_PLAYER,
                    "citizen is already in a direct conversation with another player");
        }

        UUID currentCitizenId = foregroundSessions.citizenForPlayer(playerId);
        if (currentCitizenId != null && !currentCitizenId.equals(citizenId)) {
            endConversation(playerId, false);
        }

        GeminiWsClient existingClient = foregroundSessions.client(citizenId);
        ForegroundSessionRegistry.Token existingToken = foregroundSessions.token(citizenId);
        var existingSnapshot = foregroundSessions.snapshot(citizenId).orElse(null);

        if (existingClient instanceof CitizenWsClient cws && cws.isMumbling() && cws.isPlayerTakeoverAllowed()
                && existingToken != null && existingSnapshot != null
                && existingSnapshot.priority() == ForegroundSessionRegistry.Priority.AMBIENT) {
            var promoted = foregroundSessions.promoteToPlayer(existingToken, playerId, ConversationKind.PLAYER);
            if (promoted.isEmpty()) {
                return ConversationStartResult.rejected(ConversationStartResult.Status.FAILED,
                        "ambient session ownership changed during player takeover");
            }
            dispatchLifecycleEnded(citizen, promoted.get().before().kind(), null);
            cws.transitionToPlayer(player);
            var participation = participationAdapters.get(existingToken.ownershipId());
            if (participation != null) participation.refresh();
            UrgentContactHandler.onPlayerTakeover(citizen, player);
            dispatchLifecycleStarted(citizen, ConversationKind.PLAYER, playerId);
            return ConversationStartResult.startedResult();
        }

        Runnable preemptActivity = removeActivityForPlayerPreemption(citizenId);
        if (preemptActivity != null) {
            try {
                preemptActivity.run();
            } catch (Throwable t) {
                McTalking.LOGGER.error("Failed to preempt core activity for {}", citizenId, t);
            }
        }

        if (existingToken != null) {
            if (existingClient instanceof CitizenWsClient cws && !cws.isPlayerTakeoverAllowed()) {
                cws.markPlayerTakeoverPending();
            }
            foregroundSessions.end(existingToken, ForegroundSessionRegistry.TerminalReason.REPLACED,
                    "replaced by direct player conversation");
        }

        ForegroundReservation reservation = reservePlayerForeground(citizen, playerId);
        if (reservation == null) {
            player.sendSystemMessage(Component.translatable("mc_talking.capacity_reached").withStyle(ChatFormatting.YELLOW));
            return ConversationStartResult.rejected(
                    ConversationStartResult.Status.CAPACITY_EXHAUSTED,
                    "all foreground slots are occupied by player conversations");
        }

        final CitizenWsClient ws;
        try {
            ws = new CitizenWsClient(
                    new CitizenEntityAudioProvider(citizen, McTalkingVoicechatPlugin.DIRECT_PLAYER_DIALOG),
                    citizen, player);
        } catch (RuntimeException e) {
            reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                    "failed to construct provider session");
            McTalking.LOGGER.error("Failed to construct Gemini client for player conversation with {}", citizenId, e);
            return ConversationStartResult.rejected(ConversationStartResult.Status.FAILED,
                    "failed to construct provider session");
        }

        if (!reservation.attachClient(ws)) {
            reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                    "foreground ownership changed before player client attach");
            return ConversationStartResult.rejected(ConversationStartResult.Status.FAILED,
                    "player conversation ownership changed during startup");
        }

        ws.addOnCloseAction(() -> runOnServerThread(citizen, () -> {
            var diagnostic = ws.getRecoveryDiagnostic();
            reservation.end(providerTerminalReason(ws), diagnostic.detail());
        }));

        McTalking.LOGGER.info("Starting initial websocket connection from outer...");
        try {
            ws.connect();
        } catch (RuntimeException e) {
            reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                    "failed to connect provider session");
            McTalking.LOGGER.error("Failed to connect Gemini client for player conversation with {}", citizenId, e);
            return ConversationStartResult.rejected(ConversationStartResult.Status.FAILED,
                    "failed to connect provider session");
        }

        if (!reservation.activate()) {
            reservation.end(ForegroundSessionRegistry.TerminalReason.STARTUP_FAILED,
                    "foreground ownership changed before player activation");
            return ConversationStartResult.rejected(ConversationStartResult.Status.FAILED,
                    "player conversation ownership changed during activation");
        }
        UrgentContactHandler.onPlayerTakeover(citizen, player);
        return ConversationStartResult.startedResult();
    }

    public static boolean startPlayerConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        return startPlayerConversationDetailed(player, citizen).started();
    }

    public static void endConversation(UUID playerId, boolean sendMessage) {
        endConversation(playerId, sendMessage, ForegroundSessionRegistry.TerminalReason.CANCELLED,
                "player conversation ended");
    }

    public static void endConversationForDisconnect(UUID playerId) {
        endConversation(playerId, false, ForegroundSessionRegistry.TerminalReason.PLAYER_DISCONNECTED,
                "player disconnected");
    }

    private static void endConversation(
            UUID playerId,
            boolean sendMessage,
            ForegroundSessionRegistry.TerminalReason reason,
            String detail
    ) {
        UUID citizenId = foregroundSessions.citizenForPlayer(playerId);
        if (citizenId == null) return;
        AbstractEntityCitizen entity = foregroundSessions.entityForPlayer(playerId);
        if (!foregroundSessions.endPlayer(playerId, reason, detail)) return;
        if (sendMessage && entity != null) schedulePlayerPresentationCleanup(entity, playerId, true);
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    public static boolean isPlayerInConversation(UUID playerId) {
        return foregroundSessions.citizenForPlayer(playerId) != null;
    }

    public static GeminiWsClient getClientForEntity(UUID entityId) {
        return foregroundSessions.client(entityId);
    }

    /**
     * Returns the exact current direct-player client only while its provider can accept microphone input.
     * Participation display and microphone routing therefore use the same token/player/readiness facts.
     */
    @Nullable
    public static GeminiWsClient getReadyInputClientForPlayer(UUID playerId) {
        UUID citizenId = foregroundSessions.citizenForPlayer(playerId);
        if (citizenId == null) return null;
        ForegroundSessionRegistry.Token token = foregroundSessions.token(citizenId);
        if (token == null || !conversationParticipation.canRouteInput(token, playerId)) return null;
        GeminiWsClient client = foregroundSessions.client(citizenId);
        if (client == null || client.isLifecycleClosed()) return null;
        if (client instanceof CitizenWsClient citizenClient
                && !citizenClient.isAssociatedWithPlayer(playerId)) return null;
        // The registry/client reads are individually synchronized, not one composite operation.
        // Revalidate the exact token after retrieving the client so takeover/replacement cannot
        // route a packet to a newer provider that has not reached readiness yet.
        if (!foregroundSessions.isCurrent(token) || !conversationParticipation.canRouteInput(token, playerId)) {
            return null;
        }
        return client;
    }

    public static AbstractEntityCitizen getActiveEntityForPlayer(UUID playerId) {
        return foregroundSessions.entityForPlayer(playerId);
    }

    public static UUID getPlayerForEntity(UUID entityId) {
        return foregroundSessions.playerForCitizen(entityId);
    }

    public static ConversationKind getActiveConversationKind(UUID entityId) {
        return foregroundSessions.kind(entityId);
    }

    public static void cleanup() {
        foregroundSessions.shutdown();
        backgroundSessions.shutdown();
        for (Runnable cleanup : activities.shutdown()) {
            try {
                cleanup.run();
            } catch (Throwable t) {
                McTalking.LOGGER.error("Error running activity cleanup during shutdown", t);
            }
        }
        cooldowns.clear();
        QuotaTracker.clear();
        me.sshcrack.mc_talking.manager.VoiceSelectionService.clear();
        GeminiWsClient.shutdownExecutor();
    }

}
