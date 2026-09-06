package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.mc_talking.api.conversation.AmbientLineResult;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationRules;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.manager.CitizenWsClient;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.CitizenEntityAudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import me.sshcrack.mc_talking.util.BackgroundSlotType;
import me.sshcrack.mc_talking.util.CitizenNeedAssessor;
import me.sshcrack.mc_talking.util.MumblingTopicHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Active AI clients keyed by citizen entity UUID
    private static final Map<UUID, GeminiWsClient> clients = new ConcurrentHashMap<>();

    // playerId → citizen entity the player is talking to
    private static final Map<UUID, AbstractEntityCitizen> activeEntity = new ConcurrentHashMap<>();

    // playerId → citizenId (the citizen the player is currently in conversation with)
    private static final Map<UUID, UUID> playerConversationPartners = new ConcurrentHashMap<>();

    // citizenId → playerId reverse map for O(1) getPlayerForEntity lookups
    private static final Map<UUID, UUID> citizenToPlayer = new ConcurrentHashMap<>();

    /**
     * citizenId → System.currentTimeMillis() at which their last automatic session ended.
     * Used to enforce the per-citizen cooldown configured in
     * {@link me.sshcrack.mc_talking.config.McTalkingConfig#citizenCooldownSeconds}.
     */
    private static final Map<UUID, Long> lastSessionEndTime = new ConcurrentHashMap<>();

    /**
     * citizenId → need signature captured when the cooldown was recorded.
     * If the citizen's current need signature differs, the cooldown is cleared
     * so they can immediately contact about a new problem.
     */
    private static final Map<UUID, String> lastSessionNeedSignatures = new ConcurrentHashMap<>();

    /**
     * Insertion-ordered queue of all occupied citizen slots.
     * The head (oldest) is the first eviction candidate.
     *
     * <p>This set can diverge from {@link #clients}: {@link #claimSlot} adds to
     * {@code addedEntities} before a client is created, and {@link #registerExternalClient}
     * adds to {@code clients} without touching this set. {@link #isCitizenBusy} checks
     * both, so either being present is sufficient to prevent double-booking.</p>
     */
    private static final Set<UUID> addedEntities = new LinkedHashSet<>();
    /** Claim timestamp used only to recover reservations that never attach a foreground client. */
    private static final Map<UUID, Long> foregroundSlotClaimedAtNanos = new ConcurrentHashMap<>();
    private static final long ORPHAN_FOREGROUND_SLOT_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(2);

    /** Token-owned internal activities such as Flash/TTS generation and cached playback. */
    private static final Map<UUID, CoreActivitySlot> coreBusyReservations = new ConcurrentHashMap<>();

    private static final class CoreActivitySlot {
        final UUID token;
        final long deadlineNanos;
        final Runnable onTimeout;
        final Runnable onPreempt;

        CoreActivitySlot(UUID token, long deadlineNanos, Runnable onTimeout, Runnable onPreempt) {
            this.token = token;
            this.deadlineNanos = deadlineNanos;
            this.onTimeout = onTimeout;
            this.onPreempt = onPreempt;
        }
    }

    /** Exact-ownership handle for internal non-WebSocket citizen activity. */
    public static final class CoreActivityReservation implements AutoCloseable {
        private final UUID citizenId;
        private final UUID token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private CoreActivityReservation(UUID citizenId, UUID token) {
            this.citizenId = citizenId;
            this.token = token;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            releaseCoreActivity(citizenId, token);
        }
    }

    /** Addon-owned non-conversation activity reservations keyed by citizen and opaque token. */
    private static final Map<UUID, UUID> addonBusyReservations = new ConcurrentHashMap<>();

    /**
     * Citizen UUIDs whose active conversation originated from the urgent-contact
     * walk-to-player system. Only these conversations should be auto-aborted by
     * {@link me.sshcrack.mc_talking.ServerEventHandler#checkUrgentContactAbort}
     * when the citizen's needs are resolved.
     */
    private static final Set<UUID> urgentContactConversations = ConcurrentHashMap.newKeySet();

    // ---- Background pool (configured cheap/current Live model) ----
    private static final long PREGEN_BACKGROUND_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(3);
    private static final long COMPACTION_BACKGROUND_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(10);
    private static final Map<UUID, BackgroundSlot> backgroundSlots = new LinkedHashMap<>();

    private static final class BackgroundSlot {
        final UUID token;
        final BackgroundSlotType type;
        final long deadlineNanos;
        GeminiLiveClient client;

        BackgroundSlot(UUID token, BackgroundSlotType type, long deadlineNanos) {
            this.token = token;
            this.type = type;
            this.deadlineNanos = deadlineNanos;
        }
    }

    /**
     * Ownership handle for one background task. Cleanup is token-scoped so a delayed callback
     * from an evicted task can never close a newer task for the same citizen.
     */
    public static final class BackgroundReservation implements AutoCloseable {
        private final UUID citizenId;
        private final UUID token;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private BackgroundReservation(UUID citizenId, UUID token) {
            this.citizenId = citizenId;
            this.token = token;
        }

        public boolean attachClient(GeminiLiveClient client) {
            if (closed.get()) {
                closeQuietly(client, "late background client", citizenId);
                return false;
            }
            return registerBackgroundClient(citizenId, token, client);
        }

        public boolean isActive() {
            return !closed.get() && isBackgroundReservationActive(citizenId, token);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            releaseBackgroundSlot(citizenId, token);
        }
    }

    // -------------------------------------------------------------------------
    // Slot management (priority-aware, synchronized)
    // -------------------------------------------------------------------------

    /**
     * Tries to claim one agent slot for {@code entityId}.
     *
     * <ul>
     *   <li><b>High-priority</b> ({@code isPlayerConversation=true}): evicts the
     *       oldest non-player session when at capacity, but never another player.</li>
     *   <li><b>Low-priority</b> ({@code isPlayerConversation=false}): succeeds
     *       only when a slot is genuinely free. Ambient speech never evicts an
     *       existing conversation, because doing so can cut audible speech off
     *       mid-sentence.</li>
     * </ul>
     *
     * @return {@code true} if the slot was granted
     */
    public static synchronized boolean claimSlot(AbstractEntityCitizen citizen, boolean isPlayerConversation) {
        UUID entityId = citizen.getUUID();

        // Foreground interactions always take precedence over invisible background
        // work such as greeting pregeneration or memory compaction. Keeping both
        // alive wastes free-tier quota and can make the citizen appear unavailable.
        if (backgroundSlots.containsKey(entityId)) {
            cancelBackgroundSlot(entityId, "foreground interaction started");
        }

        if (addedEntities.contains(entityId)) return true; // already registered

        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentAgents;

        if (addedEntities.size() < max) {
            addedEntities.add(entityId);
            foregroundSlotClaimedAtNanos.put(entityId, System.nanoTime());
            McTalking.LOGGER.info("[ConversationManager] Reserved slot for entity {} (Player: {})", entityId, isPlayerConversation);
            return true;
        }

        if (!isPlayerConversation) {
            return false;
        }

        // Player conversations may preempt the oldest non-player slot.
        UUID victim = findEvictableNonPlayerSlot();

        if (victim == null) {
            // Every slot belongs to a player conversation. Never terminate somebody
            // else's live conversation merely to admit a newer request.
            return false;
        }

        addedEntities.remove(victim);
        foregroundSlotClaimedAtNanos.remove(victim);

        evict(victim);
        addedEntities.add(entityId);
        foregroundSlotClaimedAtNanos.put(entityId, System.nanoTime());
        McTalking.LOGGER.info("[ConversationManager] Reserved slot for entity {} after eviction (Player: {})", entityId, isPlayerConversation);
        return true;
    }

    /**
     * Releases the slot for {@code entityId} without closing its client.
     * Use when the client is already closed externally.
     */
    public static synchronized void releaseSlot(AbstractEntityCitizen citizen) {
        releaseSlot(citizen.getUUID());
    }

    public static synchronized void releaseSlot(UUID entityId) {
        urgentContactConversations.remove(entityId);
        foregroundSlotClaimedAtNanos.remove(entityId);
        if (addedEntities.remove(entityId)) {
            McTalking.LOGGER.info("[ConversationManager] Freed slot for entity {}", entityId);
        }
    }

    /**
     * Registers a client that was created externally (e.g. a
     * {@link me.sshcrack.mc_talking.conversations.LiveConversationWsClient})
     * in the client map. The slot must already have been claimed via
     * {@link #claimSlot}.
     */
    public static synchronized void registerExternalClient(AbstractEntityCitizen citizen, GeminiWsClient client) {
        clients.put(citizen.getUUID(), client);
    }

    /**
     * Removes an externally-created client from the map and releases its slot.
     * Does <em>not</em> close the client.
     */
    public static synchronized void unregisterExternalClient(AbstractEntityCitizen citizen) {
        clients.remove(citizen.getUUID());
        releaseSlot(citizen);
    }

    /**
     * Removes an externally-created client only if it is still the currently
     * registered client for that citizen. This prevents delayed cleanup from an
     * old citizen-to-citizen session from releasing a newer player conversation.
     *
     * @return true when the expected client was removed and its slot released
     */
    public static synchronized boolean unregisterExternalClient(
            AbstractEntityCitizen citizen, GeminiWsClient expectedClient) {
        if (!clients.remove(citizen.getUUID(), expectedClient)) {
            return false;
        }
        releaseSlot(citizen);
        return true;
    }

    /**
     * Returns {@code true} if at least {@code slotsNeeded} slots are genuinely
     * free for low-priority work. Existing conversations are not counted as
     * capacity merely because they would be technically evictable.
     */
    public static synchronized boolean hasLowPriorityCapacity(int slotsNeeded) {
        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentAgents;
        int free = max - addedEntities.size();
        return free >= slotsNeeded;
    }


    /**
     * Returns {@code true} if at least {@code slotsNeeded} slots can be granted
     *
     */
    public static synchronized boolean hasFreeCapacity(int slotsNeeded) {
        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentAgents;
        int free = max - addedEntities.size();
        return free >= slotsNeeded;
    }

    // ---- Background pool methods ----

    public static synchronized boolean hasFreeBackgroundCapacity(int slotsNeeded) {
        if (slotsNeeded < 1) throw new IllegalArgumentException("slotsNeeded must be positive");
        purgeStaleBackgroundSlots();
        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentBackground;
        return (max - backgroundSlots.size()) >= slotsNeeded;
    }

    public static synchronized int getUsedBackgroundSlots() {
        purgeStaleBackgroundSlots();
        return backgroundSlots.size();
    }

    public static synchronized int getMaxBackgroundSlots() {
        return McTalkingConfig.INSTANCE.instance().maxConcurrentBackground;
    }

    /**
     * Reserves one background slot and returns its ownership handle, or {@code null} when no slot
     * is available. Compaction may evict the oldest pregeneration task, preserving the existing
     * background-priority rule.
     */
    public static synchronized BackgroundReservation reserveBackgroundSlot(
            AbstractEntityCitizen citizen,
            BackgroundSlotType type
    ) {
        purgeStaleBackgroundSlots();

        UUID id = citizen.getUUID();
        if (backgroundSlots.containsKey(id)) return null;

        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentBackground;
        if (backgroundSlots.size() >= max && type == BackgroundSlotType.COMPACTION) {
            UUID victim = null;
            for (Map.Entry<UUID, BackgroundSlot> entry : backgroundSlots.entrySet()) {
                if (entry.getValue().type == BackgroundSlotType.PREGEN) {
                    victim = entry.getKey();
                    break;
                }
            }
            if (victim != null) {
                cancelBackgroundSlot(victim, "evicted for memory compaction");
                McTalking.LOGGER.info("[ConversationManager] Evicted pregen bg slot {} for compaction {}", victim, id);
            }
        }

        if (backgroundSlots.size() >= max) return null;

        UUID token = UUID.randomUUID();
        long timeout = type == BackgroundSlotType.COMPACTION
                ? COMPACTION_BACKGROUND_TIMEOUT_NANOS
                : PREGEN_BACKGROUND_TIMEOUT_NANOS;
        backgroundSlots.put(id, new BackgroundSlot(token, type, System.nanoTime() + timeout));
        McTalking.LOGGER.info("[ConversationManager] Reserved background slot for {} (type={})", id, type);
        return new BackgroundReservation(id, token);
    }

    /** Cancels whichever background task currently belongs to this citizen. */
    public static synchronized boolean cancelBackgroundSlot(UUID entityId, String reason) {
        BackgroundSlot slot = backgroundSlots.remove(entityId);
        if (slot == null) return false;
        closeQuietly(slot.client, reason, entityId);
        McTalking.LOGGER.info("[ConversationManager] Released background slot for {} ({})", entityId, reason);
        return true;
    }

    private static synchronized boolean registerBackgroundClient(UUID entityId, UUID token, GeminiLiveClient client) {
        java.util.Objects.requireNonNull(client, "client");
        BackgroundSlot slot = backgroundSlots.get(entityId);
        if (slot == null || !slot.token.equals(token)) {
            closeQuietly(client, "background reservation no longer owned", entityId);
            return false;
        }
        if (slot.client != null && slot.client != client) {
            closeQuietly(client, "duplicate background client registration", entityId);
            return false;
        }
        slot.client = client;
        return true;
    }

    private static synchronized boolean isBackgroundReservationActive(UUID entityId, UUID token) {
        BackgroundSlot slot = backgroundSlots.get(entityId);
        return slot != null && slot.token.equals(token);
    }

    private static synchronized boolean releaseBackgroundSlot(UUID entityId, UUID token) {
        BackgroundSlot slot = backgroundSlots.get(entityId);
        if (slot == null || !slot.token.equals(token)) return false;
        backgroundSlots.remove(entityId);
        closeQuietly(slot.client, "background task completed", entityId);
        return true;
    }

    /** Runs bounded lifecycle cleanup independent of whether addon/core work requests new slots. */
    public static void tickMaintenance() {
        purgeStaleBackgroundSlots();
        purgeOrphanForegroundSlots();
        for (Runnable timeoutAction : purgeExpiredCoreActivities()) {
            try {
                timeoutAction.run();
            } catch (Throwable t) {
                McTalking.LOGGER.error("[ConversationManager] Core activity timeout cleanup failed", t);
            }
        }
    }

    private static synchronized List<Runnable> purgeExpiredCoreActivities() {
        long now = System.nanoTime();
        List<Runnable> timeoutActions = new ArrayList<>();
        var iterator = coreBusyReservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CoreActivitySlot> entry = iterator.next();
            CoreActivitySlot slot = entry.getValue();
            if (now < slot.deadlineNanos) continue;
            iterator.remove();
            timeoutActions.add(slot.onTimeout);
            McTalking.LOGGER.warn("[ConversationManager] Recovered timed-out core activity for {}", entry.getKey());
        }
        return timeoutActions;
    }

    private static synchronized void purgeStaleBackgroundSlots() {
        long now = System.nanoTime();
        var iterator = backgroundSlots.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BackgroundSlot> entry = iterator.next();
            UUID id = entry.getKey();
            BackgroundSlot slot = entry.getValue();
            boolean closed = slot.client != null && slot.client.isClosed();
            boolean expired = now >= slot.deadlineNanos;
            if (!closed && !expired) continue;

            iterator.remove();
            if (expired && !closed) {
                McTalking.LOGGER.warn("[ConversationManager] Background {} session for {} exceeded its deadline; closing it",
                        slot.type, id);
                closeQuietly(slot.client, "background session deadline exceeded", id);
            } else {
                McTalking.LOGGER.debug("[ConversationManager] Purged closed background slot for {}", id);
            }
        }
    }

    private static synchronized void purgeOrphanForegroundSlots() {
        long now = System.nanoTime();
        var iterator = addedEntities.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Long claimedAt = foregroundSlotClaimedAtNanos.get(id);
            if (claimedAt == null || now - claimedAt < ORPHAN_FOREGROUND_SLOT_TIMEOUT_NANOS) continue;
            if (clients.containsKey(id) || citizenToPlayer.containsKey(id)) continue;

            iterator.remove();
            foregroundSlotClaimedAtNanos.remove(id);
            urgentContactConversations.remove(id);
            McTalking.LOGGER.warn("[ConversationManager] Recovered orphan foreground slot for {} after timeout", id);
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

    /**
     * The oldest non-player entity in the queue that can be evicted, or {@code null}.
     */
    private static UUID findEvictableNonPlayerSlot() {
        for (UUID id : addedEntities) {
            if (getPlayerForEntity(id) == null) return id;
        }
        return null;
    }

    /**
     * Closes and removes the client for {@code entityId}.
     */
    private static void evict(UUID entityId) {
        if (entityId == null) return;

        UUID playerId = getPlayerForEntity(entityId);
        if (playerId != null) {
            // Re-add entityId because endConversation -> releaseSlot will remove it.
            // This keeps the slot reserved (counted) during the tear-down sequence.
            addedEntities.add(entityId);
            endConversation(playerId, false);
            return;
        }

        GeminiWsClient client = clients.remove(entityId);
        if (client != null) client.close();
        McTalking.LOGGER.info("[ConversationManager] Evicted slot for entity {} to make room", entityId);
    }

    // -------------------------------------------------------------------------
    // Debug / inspection accessors
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable view of all active AI clients (citizen UUID → client).
     */
    public static Map<UUID, GeminiWsClient> getClients() {
        return Collections.unmodifiableMap(clients);
    }

    /**
     * Returns an unmodifiable view of the citizen→player reverse mapping.
     */
    public static Map<UUID, UUID> getCitizenToPlayer() {
        return Collections.unmodifiableMap(citizenToPlayer);
    }

    /**
     * Returns an unmodifiable view of the player→citizen mapping.
     */
    public static Map<UUID, UUID> getPlayerConversationPartners() {
        return Collections.unmodifiableMap(playerConversationPartners);
    }

    /**
     * Returns an unmodifiable view of the last session end times.
     */
    public static Map<UUID, Long> getLastSessionEndTimes() {
        return Collections.unmodifiableMap(lastSessionEndTime);
    }

    // -------------------------------------------------------------------------
    // "Already busy" check
    // -------------------------------------------------------------------------

    /**
     * Claims a token-owned internal activity without consuming a Gemini foreground slot.
     * Timed-out reservations are removed by {@link #tickMaintenance()} and invoke the supplied
     * timeout callback after ownership has been released.
     */
    public static synchronized CoreActivityReservation reserveCoreActivity(
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
        if (clients.containsKey(citizenId) || addedEntities.contains(citizenId)
                || coreBusyReservations.containsKey(citizenId) || addonBusyReservations.containsKey(citizenId)) {
            return null;
        }

        UUID token = UUID.randomUUID();
        long timeoutNanos = unit.toNanos(timeout);
        long now = System.nanoTime();
        long deadline = timeoutNanos >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + timeoutNanos;
        coreBusyReservations.put(citizenId, new CoreActivitySlot(token, deadline, onTimeout, onPreempt));
        return new CoreActivityReservation(citizenId, token);
    }

    private static boolean releaseCoreActivity(UUID citizenId, UUID token) {
        CoreActivitySlot current = coreBusyReservations.get(citizenId);
        return current != null && current.token.equals(token)
                && coreBusyReservations.remove(citizenId, current);
    }

    /**
     * Removes the currently-owned internal activity before a player takeover and returns the
     * matching preemption callback. Removal happens before the callback runs, so cleanup caused by
     * the old activity cannot affect a replacement reservation.
     */
    private static synchronized Runnable removeCoreActivityForPreemption(UUID citizenId) {
        CoreActivitySlot slot = coreBusyReservations.remove(citizenId);
        return slot == null ? null : slot.onPreempt;
    }

    /**
     * Returns {@code true} when the citizen already has any kind of active
     * session: mumbling, player conversation, or citizen-to-citizen conversation.
     * Use this before starting any new session to avoid duplicates.
     */
    public static synchronized boolean isCitizenBusy(AbstractEntityCitizen citizen) {
        UUID id = citizen.getUUID();
        // Background work is intentionally omitted. It does not consume the
        // citizen's attention and claimSlot() cancels it if foreground speech starts.
        return clients.containsKey(id) || addedEntities.contains(id) || coreBusyReservations.containsKey(id)
                || addonBusyReservations.containsKey(id);
    }

    /**
     * Claims an addon-owned non-conversation activity reservation. The opaque token prevents a
     * stale task from releasing a newer reservation for the same citizen.
     */
    public static synchronized boolean claimAddonActivity(AbstractEntityCitizen citizen, UUID token) {
        UUID citizenId = citizen.getUUID();
        if (clients.containsKey(citizenId) || addedEntities.contains(citizenId)
                || coreBusyReservations.containsKey(citizenId) || addonBusyReservations.containsKey(citizenId)) {
            return false;
        }
        addonBusyReservations.put(citizenId, token);
        return true;
    }

    /** Releases exactly the addon reservation identified by {@code token}. */
    public static boolean releaseAddonActivity(UUID citizenId, UUID token) {
        return addonBusyReservations.remove(citizenId, token);
    }

    /** Requests the active citizen session to finish its current audible turn and close. */
    public static boolean requestGracefulEnd(AbstractEntityCitizen citizen) {
        GeminiWsClient client = clients.get(citizen.getUUID());
        if (client == null) return false;
        client.endConversationWhenPossible();
        return true;
    }

    /**
     * Records that the citizen's automatic session just ended, starting their cooldown timer.
     * Call this whenever a mumbling or citizen-to-citizen session concludes naturally
     * (not when a session is evicted to make room for a player).
     */
    public static void recordCooldown(AbstractEntityCitizen citizen) {
        lastSessionEndTime.put(citizen.getUUID(), System.currentTimeMillis());
        lastSessionNeedSignatures.put(citizen.getUUID(), computeNeedSignature(citizen));
    }

    /**
     * Returns {@code true} if the citizen is still within their cooldown period and
     * should not be selected for a new automatic (mumble / citizen-to-citizen) session.
     *
     * <p>If the citizen's current needs differ from when the cooldown was recorded,
     * the cooldown is cleared so they can immediately contact about the new problem.</p>
     */
    public static boolean isCitizenOnCooldown(AbstractEntityCitizen citizen) {
        UUID citizenId = citizen.getUUID();
        Long lastEnd = lastSessionEndTime.get(citizenId);
        if (lastEnd == null) return false;

        // Check if the citizen's needs have changed since cooldown was recorded
        String prevSignature = lastSessionNeedSignatures.get(citizenId);
        String currentSignature = computeNeedSignature(citizen);
        if (prevSignature != null && !prevSignature.equals(currentSignature)) {
            lastSessionEndTime.remove(citizenId);
            lastSessionNeedSignatures.remove(citizenId);
            return false;
        }

        long cooldownMs = McTalkingConfig.INSTANCE.instance().citizenCooldownSeconds * 1000L;
        return (System.currentTimeMillis() - lastEnd) < cooldownMs;
    }

    /**
     * ONLY used when a debug command is invoked that explicitly tells to remove the cooldown.
     * @param citizen the citizen to remove the cooldown for
     */
    public static void forceRemoveCooldown(AbstractEntityCitizen citizen) {
        UUID id = citizen.getUUID();
        lastSessionEndTime.remove(id);
        lastSessionNeedSignatures.remove(id);
    }

    /**
     * Computes a signature string representing the citizen's current urgent needs.
     * If this signature changes between sessions, the cooldown is cleared so the
     * citizen can immediately contact about a new problem.
     */
    public static String computeNeedSignature(AbstractEntityCitizen citizen) {
        return CitizenNeedAssessor.computeNeedSignature(citizen);
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

    /**
     * Context-aware speech eligibility used by core and addon integrations. Core invariants are
     * evaluated first; registered addon policies can only veto an otherwise-eligible citizen.
     */
    public static boolean canCitizenSpeak(AbstractEntityCitizen citizen, ConversationKind kind) {
        boolean playerRequest = kind == ConversationKind.PLAYER;
        boolean coreAllows = !citizen.isSleeping()
                && !(citizen instanceof VisitorCitizen)
                && (playerRequest || (!isCitizenOnCooldown(citizen) && !isCitizenBusy(citizen)));
        return coreAllows && CitizenConversationRules.addonsAllowSpeech(citizen, kind);
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
     * Starts a citizen speaking urgently to a nearby player when the citizen has pressing needs
     * (low-priority, spatial audio — the player hears it positionally and can choose to respond
     * by using the Citizen Communication Device).
     *
     * <p>The prompt instructs the citizen to address the player by name rather than muttering
     * to themselves, distinguishing this from ordinary mumbling. Silently returns if the citizen
     * is already busy, on cooldown, or no low-priority slot is available.</p>
     */
    public static boolean startUrgentContact(AbstractEntityCitizen citizen, ServerPlayer player) {
        if (!McTalkingConfig.hasGeminiApiKey()) return false;
        boolean started = startLowPrioritySession(citizen,
                MumblingTopicHelper.buildUrgentContactPrompt(citizen, player.getName().getString()),
                ConversationKind.URGENT_CONTACT);
        if (started) {
            urgentContactConversations.add(citizen.getUUID());
        }
        return started;
    }

    /**
     * Starts a low-priority, one-sided AI voice session for {@code citizen}.
     *
     * <h4>How it works under the hood</h4>
     * <p>This method opens a <em>Gemini Live WebSocket</em> in system-controlled
     * (mumbling) mode.  The session uses
     * {@link me.sshcrack.mc_talking.api.prompt.CitizenPromptService#generateSystemControlledRoleplayPrompt}
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
     *   <li>A low-priority slot must be available via {@link #claimSlot}.</li>
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
        return startLowPrioritySession(citizen, userPrompt, ConversationKind.ADDON_AMBIENT, completion);
    }

    /** Cancels an addon/system ambient session without exposing its Gemini client. */
    public static boolean cancelAddonAmbientSession(AbstractEntityCitizen citizen) {
        if (getPlayerForEntity(citizen.getUUID()) != null) return false;
        GeminiWsClient client = clients.get(citizen.getUUID());
        if (!(client instanceof CitizenWsClient cws) || !cws.isMumbling()) return false;
        cws.close();
        return true;
    }

    private static boolean startLowPrioritySession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            ConversationKind kind
    ) {
        return startLowPrioritySession(citizen, userPrompt, kind, null);
    }

    private static boolean startLowPrioritySession(
            AbstractEntityCitizen citizen,
            String userPrompt,
            ConversationKind kind,
            Consumer<AmbientLineResult> completion
    ) {
        if (!McTalkingConfig.hasGeminiApiKey()) return false;
        if (!canCitizenSpeak(citizen, kind)) return false;

        UUID citizenId = citizen.getUUID();

        if (!claimSlot(citizen, false)) {
            McTalking.LOGGER.debug("[ConversationManager] No low-priority slot available for session for citizen {}", citizenId);
            return false;
        }

        try {
            AtomicBoolean audibleCompletion = new AtomicBoolean(false);
            final CitizenWsClient[] holder = new CitizenWsClient[1];
            var client = new CitizenWsClient(citizen, c -> {
                String transcript = c.getSessionTranscriptSnapshot();
                audibleCompletion.set(true);
                c.close();
                if (completion != null) {
                    var server = citizen.level().getServer();
                    Runnable notify = () -> completion.accept(AmbientLineResult.completed(transcript));
                    if (server != null && !server.isSameThread()) server.execute(notify);
                    else notify.run();
                }
            });
            holder[0] = client;
            client.addOnCloseAction(() -> {
                var server = citizen.level().getServer();
                Runnable cleanup = () -> {
                    synchronized (ConversationManager.class) {
                        if (clients.get(citizenId) == holder[0]) {
                            clients.remove(citizenId);
                            releaseSlot(citizen);
                            recordCooldown(citizen);
                        }
                    }
                    if (completion != null && !audibleCompletion.get()) {
                        completion.accept(AmbientLineResult.failed("ambient session closed before audible completion"));
                    }
                };
                if (server != null && !server.isSameThread()) server.execute(cleanup);
                else cleanup.run();
            });
            client.addPromptTextAfterTalkingComplete(userPrompt);
            clients.put(citizenId, client);
            return true;
        } catch (RuntimeException e) {
            releaseSlot(citizen);
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
    public static boolean startPlayerConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (!McTalkingConfig.hasGeminiApiKey()) {
            player.sendSystemMessage(
                    Component.translatable("mc_talking.no_key")
                            .withStyle(ChatFormatting.RED));
            return false;
        }

        if (!canCitizenSpeak(citizen, ConversationKind.PLAYER))
            return false;

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        UUID existingPlayerId = citizenToPlayer.get(citizenId);
        if (existingPlayerId != null && !existingPlayerId.equals(playerId)) {
            // Check the requested citizen before tearing down the caller's current
            // conversation. A failed switch must leave the old conversation intact.
            player.sendSystemMessage(Component.translatable("mc_talking.citizen_in_use")
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }

        UUID currentCitizenId = playerConversationPartners.get(playerId);
        if (currentCitizenId != null && !currentCitizenId.equals(citizenId)) {
            // A player can only own one direct conversation. Tear the old one down
            // now that the requested target has passed its ownership check.
            endConversation(playerId, false);
        }

        activeEntity.put(playerId, citizen);
        citizenToPlayer.put(citizenId, playerId);

        GeminiWsClient existingClient = clients.get(citizenId);

        if (existingClient instanceof CitizenWsClient cws && cws.isMumbling()) {
            // Reuse the mumbling session – slot is already held
            cws.transitionToPlayer(player);
        } else {
            // If the citizen is busy via a token-owned non-WebSocket activity (e.g. Flash/TTS
            // or cached playback), release that exact ownership before running its preemption
            // callback. Delayed cleanup from it can no longer disturb the player session.
            Runnable preemptActivity = removeCoreActivityForPreemption(citizenId);
            if (preemptActivity != null) {
                try {
                    preemptActivity.run();
                } catch (Throwable t) {
                    McTalking.LOGGER.error("Failed to preempt core activity for {}", citizenId, t);
                }
            }

            // Close any non-player session that is occupying this citizen's slot
            if (existingClient != null) {
                existingClient.close();
                clients.remove(citizenId);
                // slot stays in addedEntities; claimSlot will see it already present
            }

            // High-priority claim may evict ambient work, but never another player.
            if (!claimSlot(citizen, true)) {
                activeEntity.remove(playerId);
                citizenToPlayer.remove(citizenId, playerId);
                player.sendSystemMessage(Component.translatable("mc_talking.capacity_reached")
                        .withStyle(ChatFormatting.YELLOW));
                return false;
            }

            final CitizenWsClient ws;
            try {
                ws = new CitizenWsClient(
                        new CitizenEntityAudioProvider(citizen, McTalkingVoicechatPlugin.DIRECT_PLAYER_DIALOG),
                        citizen, player);
            } catch (RuntimeException e) {
                releaseSlot(citizen);
                activeEntity.remove(playerId);
                citizenToPlayer.remove(citizenId, playerId);
                McTalking.LOGGER.error("Failed to construct Gemini client for player conversation with {}", citizenId, e);
                return false;
            }

            clients.put(citizenId, ws);

            // Eagerly connect so the WebSocket is already establishing when the first
            // input arrives.  ensureConnectionForQueuedInput() in GeminiWsClient also
            // handles the initial-connection path (via hasMadeInitialConnection), so a
            // lazy approach would also work — but eager connect reduces first-input latency.
            McTalking.LOGGER.info("Starting initial websocket connection from outer...");
            try {
                ws.connect();
            } catch (RuntimeException e) {
                clients.remove(citizenId, ws);
                releaseSlot(citizen);
                activeEntity.remove(playerId);
                citizenToPlayer.remove(citizenId, playerId);
                try { ws.close(); } catch (Exception ignored) { }
                McTalking.LOGGER.error("Failed to connect Gemini client for player conversation with {}", citizenId, e);
                return false;
            }
        }

        playerConversationPartners.put(playerId, citizenId);
        urgentContactConversations.remove(citizenId);
        return true;
    }

    /**
     * Ends a conversation for a specific player.
     *
     * @param playerId    The player's UUID
     * @param sendMessage Whether to send a "too far" message to the player
     */
    public static void endConversation(UUID playerId, boolean sendMessage) {
        UUID citizenId = playerConversationPartners.remove(playerId);
        if (citizenId == null) return;

        urgentContactConversations.remove(citizenId);
        citizenToPlayer.remove(citizenId);
        GeminiWsClient client = clients.remove(citizenId);
        if (client != null) client.close();

        AbstractEntityCitizen entity = activeEntity.remove(playerId);
        if (entity != null) releaseSlot(entity);
        if (entity != null && entity.isAlive()) {
            var server = entity.level().getServer();
            if (server == null)
                return;

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
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

                AiStatusHelper.setAiStatusSynced(entity, AiStatus.NONE);
                if (sendMessage) {
                    player.sendSystemMessage(Component.translatable("mc_talking.too_far")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Query helpers
    // -------------------------------------------------------------------------

    public static boolean isPlayerInConversation(UUID playerId) {
        return playerConversationPartners.containsKey(playerId);
    }

    public static GeminiWsClient getClientForEntity(UUID entityId) {
        return clients.get(entityId);
    }

    public static AbstractEntityCitizen getActiveEntityForPlayer(UUID playerId) {
        return activeEntity.get(playerId);
    }

    /**
     * @return {@code true} if the given citizen's conversation originated from
     * the urgent-contact walk-to-player system
     */
    public static boolean isUrgentConversation(UUID citizenId) {
        return urgentContactConversations.contains(citizenId);
    }

    /**
     * Returns the player UUID that is actively conversing with the given citizen, or {@code null}.
     */
    public static UUID getPlayerForEntity(UUID entityId) {
        return citizenToPlayer.get(entityId);
    }

    public static void cleanup() {
        for (GeminiWsClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                McTalking.LOGGER.error("Error closing client during cleanup", e);
            }
        }
        for (BackgroundSlot slot : backgroundSlots.values()) {
            closeQuietly(slot.client, "server shutdown", null);
        }
        clients.clear();
        activeEntity.clear();
        playerConversationPartners.clear();
        citizenToPlayer.clear();
        addedEntities.clear();
        foregroundSlotClaimedAtNanos.clear();
        backgroundSlots.clear();
        coreBusyReservations.clear();
        addonBusyReservations.clear();
        lastSessionEndTime.clear();
        lastSessionNeedSignatures.clear();
        QuotaTracker.clear();
        me.sshcrack.mc_talking.manager.VoiceSelectionService.clear();
        GeminiWsClient.shutdownExecutor();
    }
}
