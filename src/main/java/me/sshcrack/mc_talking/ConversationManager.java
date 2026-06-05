package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.manager.CitizenWsClient;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import me.sshcrack.mc_talking.util.MumblingTopicHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li><b>High-priority (player conversations)</b>: always succeed. When all
 *       {@code maxConcurrentAgents} slots are full they evict the oldest
 *       <em>non-player</em> session to make room.</li>
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
     */
    private static final Set<UUID> addedEntities = new LinkedHashSet<>();

    /**
     * Set of citizens that are currently occupied by a conversation or audio
     * playback that does not use a Gemini Live WebSocket nor a slot reservation.
     *
     * <p>Use {@link #markBusy} / {@link #markNotBusy} to manage this set.
     * Citizens in this set will be reported as busy by {@link #isCitizenBusy},
     * preventing them from being selected for new sessions, but they do
     * <em>not</em> count toward {@link McTalkingConfig#maxConcurrentAgents} and are never
     * evicted — they are simply blocked from starting new sessions.</p>
     */
    private static final Set<UUID> busyEntities = ConcurrentHashMap.newKeySet();

    /**
     * Citizen UUIDs whose active conversation originated from the urgent-contact
     * walk-to-player system. Only these conversations should be auto-aborted by
     * {@link me.sshcrack.mc_talking.ServerEventHandler#checkUrgentContactAbort}
     * when the citizen's needs are resolved.
     */
    private static final Set<UUID> urgentContactConversations = ConcurrentHashMap.newKeySet();

    /**
     * Citizen UUID → handler that aborts a non-WebSocket session (e.g. Flash/TTS
     * conversation) so a player conversation can take over.
     */
    private static final Map<UUID, Runnable> abortHandlers = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Slot management (priority-aware, synchronized)
    // -------------------------------------------------------------------------

    /**
     * Tries to claim one agent slot for {@code entityId}.
     *
     * <ul>
     *   <li><b>High-priority</b> ({@code isPlayerConversation=true}): always
     *       succeeds; evicts the oldest non-player session when at capacity.</li>
     *   <li><b>Low-priority</b> ({@code isPlayerConversation=false}): succeeds
     *       only when a slot is free or a non-player slot can be evicted without
     *       displacing any player. Returns {@code false} otherwise.</li>
     * </ul>
     *
     * @return {@code true} if the slot was granted
     */
    public static synchronized boolean claimSlot(AbstractEntityCitizen citizen, boolean isPlayerConversation) {
        UUID entityId = citizen.getUUID();
        if (addedEntities.contains(entityId)) return true; // already registered

        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentAgents;

        if (addedEntities.size() < max) {
            addedEntities.add(entityId);
            McTalking.LOGGER.info("[ConversationManager] Reserved slot for entity {} (Player: {})", entityId, isPlayerConversation);
            return true;
        }

        // At capacity – find a non-player slot to evict
        UUID victim = findEvictableNonPlayerSlot();

        if (victim == null) {
            // Every slot belongs to a player conversation
            if (!isPlayerConversation) {
                // Low priority – refuse so no player is displaced
                return false;
            }
            // High priority last-resort: evict the oldest slot even if it is a player
            var it = addedEntities.iterator();
            victim = it.hasNext() ? it.next() : null;
            if (victim != null) it.remove();
        } else {
            addedEntities.remove(victim);
        }

        evict(victim);
        addedEntities.add(entityId);
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
     * Returns {@code true} if at least {@code slotsNeeded} slots can be granted
     * at low priority (free slots + evictable non-player slots ≥ slotsNeeded).
     */
    public static synchronized boolean hasLowPriorityCapacity(int slotsNeeded) {
        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentAgents;
        int free = max - addedEntities.size();
        if (free >= slotsNeeded) return true;

        int evictable = 0;
        for (UUID id : addedEntities) {
            if (getPlayerForEntity(id) == null) evictable++;
        }
        return (free + evictable) >= slotsNeeded;
    }


    /**
     * Returns {@code true} if at least {@code slotsNeeded} slots can be granted
     * */
    public static synchronized boolean hasFreeCapacity(int slotsNeeded) {
        int max = McTalkingConfig.INSTANCE.instance().maxConcurrentAgents;
        int free = max - addedEntities.size();
        return free >= slotsNeeded;
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
     * Marks the citizen as busy without claiming a slot.
     *
     * <p>Use this only for sessions that consume the citizen's attention but
     * do not need a Gemini Live WebSocket connection or a slot reservation
     * (e.g. Flash/TTS-based citizen-to-citizen conversations). The citizen
     * will be reported as busy by {@link #isCitizenBusy}, preventing
     * double-booking, but will not count toward {@link McTalkingConfig#maxConcurrentAgents}
     * and will not be evictable.
     *
     * <p>Every call must be paired with a corresponding {@link #markNotBusy}
     * call (typically in a {@code finally} block).
     */
    public static void markBusy(AbstractEntityCitizen citizen) {
        busyEntities.add(citizen.getUUID());
    }

    /**
     * Removes the busy mark previously set by {@link #markBusy}.
     *
     * <p>Call this when the session that called {@link #markBusy} ends
     * (typically in a {@code finally} block).
     */
    public static void markNotBusy(AbstractEntityCitizen citizen) {
        busyEntities.remove(citizen.getUUID());
    }

    /**
     * Registers an abort handler for a citizen that is occupied by a
     * non-WebSocket session (e.g. a Flash/TTS conversation).
     *
     * <p>When {@link #startPlayerConversation} takes over this citizen,
     * the handler is executed so the session can stop its work and skip
     * any lifecycle cleanup that would interfere with the new conversation.
     *
     * <p>Must be paired with {@link #unregisterAbortHandler} in a {@code finally} block.
     */
    public static void registerAbortHandler(AbstractEntityCitizen citizen, Runnable handler) {
        abortHandlers.put(citizen.getUUID(), handler);
    }

    /**
     * Removes the abort handler previously registered by {@link #registerAbortHandler}.
     */
    public static void unregisterAbortHandler(AbstractEntityCitizen citizen) {
        abortHandlers.remove(citizen.getUUID());
    }

    /**
     * Returns {@code true} when the citizen already has any kind of active
     * session: mumbling, player conversation, or citizen-to-citizen conversation.
     * Use this before starting any new session to avoid duplicates.
     */
    public static synchronized boolean isCitizenBusy(AbstractEntityCitizen citizen) {
        return clients.containsKey(citizen.getUUID()) || addedEntities.contains(citizen.getUUID()) || busyEntities.contains(citizen.getUUID());
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
     * Computes a signature string representing the citizen's current urgent needs.
     * If this signature changes between sessions, the cooldown is cleared so the
     * citizen can immediately contact about a new problem.
     */
    public static String computeNeedSignature(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data == null) return "none";

        List<String> needs = new ArrayList<>();

        if (data.getJobStatus() == JobStatus.STUCK) needs.add("stuck");
        if (data.getCitizenDiseaseHandler().isSick()) needs.add("sick");

        double saturation = data.getSaturation();
        if (saturation <= 1) {
            needs.add("starving");
        } else if (saturation <= 3) {
            needs.add("hungry");
        }

        if (data.getHomeBuilding() == null) needs.add("homeless");

        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        if (happiness < 3.0) {
            needs.add("very_unhappy");
        } else if (happiness < 5.0) {
            needs.add("unhappy");
        }

        var entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            double healthPercent = (entityOpt.get().getHealth() / Math.max(1.0, entityOpt.get().getMaxHealth())) * 100.0;
            if (healthPercent < 25.0) {
                needs.add("low_health");
            } else if (healthPercent < 50.0) {
                needs.add("medium_health");
            }
        }

        if (needs.isEmpty()) needs.add("none");
        return String.join(",", needs);
    }

    // -------------------------------------------------------------------------
    // Public conversation API
    // -------------------------------------------------------------------------

    public static boolean canCitizenSpeak(AbstractEntityCitizen citizen) {
        return canCitizenSpeak(citizen, false);
    }

    public static boolean canCitizenSpeak(AbstractEntityCitizen citizen, boolean isPlayerRequest) {
        return !citizen.isSleeping()
                && !(citizen instanceof VisitorCitizen)
                && (isPlayerRequest || !isCitizenOnCooldown(citizen))
                && (isPlayerRequest || !isCitizenBusy(citizen));
    }


    /**
     * Starts a citizen mumbling to itself when a player is nearby (low-priority).
     *
     * <p>Silently returns if the citizen is already busy or if no low-priority
     * slot is available (pool is full of player conversations).</p>
     */
    public static void startMumbling(AbstractEntityCitizen citizen) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;
        if (!canCitizenSpeak(citizen)) return;
        startLowPrioritySession(citizen, MumblingTopicHelper.buildPrompt(citizen));
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
    public static void startUrgentContact(AbstractEntityCitizen citizen, ServerPlayer player) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;
        if (!canCitizenSpeak(citizen)) return;
        startLowPrioritySession(citizen, MumblingTopicHelper.buildUrgentContactPrompt(citizen, player.getName().getString()));
        if (clients.containsKey(citizen.getUUID())) {
            urgentContactConversations.add(citizen.getUUID());
        }
    }

    /**
     * Helper method for starting low-priority sessions (mumbling or urgent contact).
     * Handles slot claiming, client creation, and cooldown recording.
     */
    private static void startLowPrioritySession(AbstractEntityCitizen citizen, String prompt) {
        UUID citizenId = citizen.getUUID();

        // Low-priority: refuse if doing so would evict a player session
        if (!claimSlot(citizen, false)) {
            McTalking.LOGGER.debug("[ConversationManager] No low-priority slot available for session for citizen {}", citizenId);
            return;
        }

        var client = new CitizenWsClient(citizen,
                c -> {
                    c.close();
                    synchronized (ConversationManager.class) {
                        if (clients.get(citizenId) == c) {
                            clients.remove(citizenId);
                            releaseSlot(citizen);
                        }
                    }
                    // Record cooldown so this citizen won't be immediately re-selected
                    recordCooldown(citizen);
                });
        client.addPromptTextAfterTalkingComplete(prompt);
        clients.put(citizenId, client);
    }

    /**
     * <p>If the citizen is already mumbling the existing session is reused
     * (no reconnect). If the citizen is in a different active session (citizen-to-
     * citizen) it is closed first so the player always wins.</p>
     */
    public static void startPlayerConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("mc_talking.no_key")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        if (!canCitizenSpeak(citizen, true))
            return;

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        UUID existingPlayerId = citizenToPlayer.get(citizenId);
        if (existingPlayerId != null && !existingPlayerId.equals(playerId)) {
            endConversation(existingPlayerId, false);
        }

        activeEntity.put(playerId, citizen);
        citizenToPlayer.put(citizenId, playerId);

        GeminiWsClient existingClient = clients.get(citizenId);

        if (existingClient instanceof CitizenWsClient cws && cws.isMumbling()) {
            // Reuse the mumbling session – slot is already held
            cws.transitionToPlayer(player);
        } else {
            // If the citizen is busy via a non-WebSocket session (e.g. Flash/TTS),
            // abort it so the player can take over
            Runnable abortHandler = abortHandlers.remove(citizenId);
            if (abortHandler != null) {
                abortHandler.run();
            }

            // Close any non-player session that is occupying this citizen's slot
            if (existingClient != null) {
                existingClient.close();
                clients.remove(citizenId);
                // slot stays in addedEntities; claimSlot will see it already present
            }

            // High-priority claim (may evict an older non-player slot if at capacity)
            claimSlot(citizen, true);
            var ws = new CitizenWsClient(
                    new CitzienEntityAudioProvider(citizen, McTalkingVoicechatPlugin.DIRECT_PLAYER_DIALOG),
                    citizen, player);

            clients.put(citizenId, ws);

            // Eagerly connect so the WebSocket is already establishing when the first
            // input arrives.  ensureConnectionForQueuedInput() in GeminiWsClient also
            // handles the initial-connection path (via hasMadeInitialConnection), so a
            // lazy approach would also work — but eager connect reduces first-input latency.
            McTalking.LOGGER.info("Starting initial websocket connection from outer...");
            ws.connect();
        }

        playerConversationPartners.put(playerId, citizenId);
        urgentContactConversations.remove(citizenId);
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
        clients.clear();
        activeEntity.clear();
        playerConversationPartners.clear();
        citizenToPlayer.clear();
        addedEntities.clear();
        busyEntities.clear();
        abortHandlers.clear();
        lastSessionEndTime.clear();
        lastSessionNeedSignatures.clear();
        GeminiWsClient.shutdownExecutor();
    }
}
