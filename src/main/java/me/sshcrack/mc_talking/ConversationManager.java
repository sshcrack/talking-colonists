package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.sshcrack.mc_talking.config.McTalkingConfig;

/*? if neoforge {*/
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
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
    private static final Map<UUID, GeminiWsClient> clients = new HashMap<>();

    // playerId → citizen entity the player is talking to
    private static final Map<UUID, AbstractEntityCitizen> activeEntity = new HashMap<>();

    // playerId → citizenId (the citizen the player is currently in conversation with)
    private static final Map<UUID, UUID> playerConversationPartners = new HashMap<>();

    // citizenId → playerId reverse map for O(1) getPlayerForEntity lookups
    private static final Map<UUID, UUID> citizenToPlayer = new HashMap<>();

    /**
     * citizenId → System.currentTimeMillis() at which their last automatic session ended.
     * Used to enforce the per-citizen cooldown configured in
     * {@link me.sshcrack.mc_talking.config.McTalkingConfig#citizenCooldownSeconds}.
     */
    private static final Map<UUID, Long> lastSessionEndTime = new ConcurrentHashMap<>();

    /**
     * Insertion-ordered queue of all occupied citizen slots.
     * The head (oldest) is the first eviction candidate.
     */
    private static final Queue<UUID> addedEntities = new LinkedList<>();

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
    public static synchronized boolean claimSlot(UUID entityId, boolean isPlayerConversation) {
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
            victim = addedEntities.poll();
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
    public static synchronized void releaseSlot(UUID entityId) {
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
    public static synchronized void registerExternalClient(UUID entityId, GeminiWsClient client) {
        clients.put(entityId, client);
    }

    /**
     * Removes an externally-created client from the map and releases its slot.
     * Does <em>not</em> close the client.
     */
    public static synchronized void unregisterExternalClient(UUID entityId) {
        clients.remove(entityId);
        releaseSlot(entityId);
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
        GeminiWsClient client = clients.remove(entityId);
        if (client != null) client.close();
        McTalking.LOGGER.info("[ConversationManager] Evicted slot for entity {} to make room", entityId);
    }

    // -------------------------------------------------------------------------
    // "Already busy" check
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the citizen already has any kind of active
     * session: mumbling, player conversation, or citizen-to-citizen conversation.
     * Use this before starting any new session to avoid duplicates.
     */
    public static synchronized boolean isCitizenBusy(UUID citizenId) {
        return clients.containsKey(citizenId);
    }

    /**
     * Records that the citizen's automatic session just ended, starting their cooldown timer.
     * Call this whenever a mumbling or citizen-to-citizen session concludes naturally
     * (not when a session is evicted to make room for a player).
     */
    public static void recordCooldown(UUID citizenId) {
        lastSessionEndTime.put(citizenId, System.currentTimeMillis());
    }

    /**
     * Returns {@code true} if the citizen is still within their cooldown period and
     * should not be selected for a new automatic (mumble / citizen-to-citizen) session.
     */
    public static boolean isCitizenOnCooldown(UUID citizenId) {
        Long lastEnd = lastSessionEndTime.get(citizenId);
        if (lastEnd == null) return false;
        long cooldownMs = McTalkingConfig.INSTANCE.instance().citizenCooldownSeconds * 1000L;
        return (System.currentTimeMillis() - lastEnd) < cooldownMs;
    }

    // -------------------------------------------------------------------------
    // Public conversation API
    // -------------------------------------------------------------------------

    /**
     * Starts a citizen mumbling to itself when a player is nearby (low-priority).
     *
     * <p>Silently returns if the citizen is already busy or if no low-priority
     * slot is available (pool is full of player conversations).</p>
     */
    public static void startMumbling(AbstractEntityCitizen citizen) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;

        UUID citizenId = citizen.getUUID();

        // Busy guard: citizen already has any kind of active session
        if (isCitizenBusy(citizenId)) return;

        // Low-priority: refuse if doing so would evict a player session
        if (!claimSlot(citizenId, false)) {
            McTalking.LOGGER.debug("[ConversationManager] No low-priority slot available for mumbling citizen {}", citizenId);
            return;
        }

        var client = new CitizenWsClient(citizen,
                c -> {
                    c.close();
                    synchronized (ConversationManager.class) {
                        if (clients.get(citizenId) == c) {
                            clients.remove(citizenId);
                            releaseSlot(citizenId);
                        }
                    }
                    // Record cooldown so this citizen won't be immediately re-selected
                    recordCooldown(citizenId);
                });
        client.addPromptTextAfterTalkingComplete(MumblingTopicHelper.buildPrompt(citizen));
        clients.put(citizenId, client);
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

        UUID citizenId = citizen.getUUID();

        if (isCitizenBusy(citizenId)) return;

        if (!claimSlot(citizenId, false)) {
            McTalking.LOGGER.debug("[ConversationManager] No low-priority slot available for citizen-initiated contact {}", citizenId);
            return;
        }

        var client = new CitizenWsClient(citizen,
                c -> {
                    c.close();
                    synchronized (ConversationManager.class) {
                        if (clients.get(citizenId) == c) {
                            clients.remove(citizenId);
                            releaseSlot(citizenId);
                        }
                    }
                    recordCooldown(citizenId);
                });
        client.addPromptTextAfterTalkingComplete(MumblingTopicHelper.buildUrgentContactPrompt(citizen, player.getName().getString()));
        clients.put(citizenId, client);
    }

    /**
     * <p>If the citizen is already mumbling the existing session is reused
     * (no reconnect). If the citizen is in a different active session (citizen-to-
     * citizen) it is closed first so the player always wins.</p>
     */
    public static void startConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("mc_talking.no_key")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        activeEntity.put(playerId, citizen);
        citizenToPlayer.put(citizenId, playerId);

        GeminiWsClient existingClient = clients.get(citizenId);

        if (existingClient instanceof CitizenWsClient cws && cws.isMumbling()) {
            // Reuse the mumbling session – slot is already held
            cws.transitionToPlayer(player);
        } else {
            // Close any non-player session that is occupying this citizen's slot
            if (existingClient != null) {
                existingClient.close();
                clients.remove(citizenId);
                // slot stays in addedEntities; claimSlot will see it already present
            }

            // High-priority claim (may evict an older non-player slot if at capacity)
            claimSlot(citizenId, true);
            clients.put(citizenId, new CitizenWsClient(
                    new CitzienEntityAudioProvider(citizen, McTalkingVoicechatPlugin.DIRECT_PLAYER_DIALOG),
                    citizen, player));
        }

        playerConversationPartners.put(playerId, citizenId);
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

        citizenToPlayer.remove(citizenId);
        GeminiWsClient client = clients.remove(citizenId);
        if (client != null) client.close();
        releaseSlot(citizenId);

        AbstractEntityCitizen entity = activeEntity.remove(playerId);
        if (entity != null && entity.isAlive()) {
            ServerPlayer player = entity.level().getServer().getPlayerList().getPlayer(playerId);
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
     * Returns the player UUID that is actively conversing with the given citizen, or {@code null}.
     */
    public static UUID getPlayerForEntity(UUID entityId) {
        return citizenToPlayer.get(entityId);
    }

    public static UUID getPlayerConversationPartner(UUID playerId) {
        return playerConversationPartners.get(playerId);
    }

    public static boolean isCitizenMumbling(UUID citizenId) {
        GeminiWsClient client = clients.get(citizenId);
        return client instanceof CitizenWsClient c && c.isMumbling();
    }

    public static void cleanup() {
        for (GeminiWsClient client : clients.values()) client.close();
        clients.clear();
        activeEntity.clear();
        playerConversationPartners.clear();
        citizenToPlayer.clear();
        addedEntities.clear();
        lastSessionEndTime.clear();
    }
}
