package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.manager.TalkingManager;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Manages conversations between players and citizens, including tracking
 * active conversations, entity focus, and handling conversation lifecycle.
 */
public class ConversationManager {
    // Track AI status for each entity
    private static final Map<UUID, AiStatus> aiStatus = new HashMap<>();

    // Track active AI clients for each entity
    private static final Map<UUID, TalkingManager> clients = new HashMap<>();

    // Track which entity each player is actively conversing with
    private static final Map<UUID, AbstractEntityCitizen> activeEntity = new HashMap<>();

    // Track which player is conversing with which entity
    private static final Map<UUID, UUID> playerConversationPartners = new HashMap<>();

    // Queue of entities recently added for conversation, for managing concurrent limits
    private static final Queue<UUID> addedEntities = new LinkedList<>();

    // Track which entity each player is looking at
    private static final Map<UUID, UUID> playerLookingAt = new HashMap<>();

    // Track how long each player has been looking at an entity (in ticks)
    private static final Map<UUID, Integer> lookDuration = new HashMap<>();

    // Track the previous entity that was looked at for tolerance handling
    private static final Map<UUID, UUID> previousEntityLookedAt = new HashMap<>();

    // Track when the last entity switch occurred
    private static final Map<UUID, Long> lastEntitySwitchTime = new HashMap<>();

    /**
     * Updates the AI status for a specific entity
     *
     * @param entityId The UUID of the entity
     * @param status   The AI status to set
     */
    public static void updateAiStatus(UUID entityId, AiStatus status) {
        aiStatus.put(entityId, status);
    }

    /**
     * Gets the current AI status for an entity
     *
     * @param entityId The UUID of the entity
     * @return The current AI status, or NONE if not set
     */
    public static AiStatus getAiStatus(UUID entityId) {
        return aiStatus.getOrDefault(entityId, AiStatus.NONE);
    }

    /**
     * Gets all current AI status mappings
     *
     * @return Map of entity UUIDs to their AI status
     */
    public static Map<UUID, AiStatus> getAllAiStatus() {
        return Collections.unmodifiableMap(aiStatus);
    }

    /**
     * Clears all AI status data (typically on disconnect)
     */
    public static void clearAiStatus() {
        aiStatus.clear();
    }

    /**
     * Adds an entity to the managed entities queue, respecting the maximum concurrent limit
     *
     * @param entityId The UUID of the entity to add
     */
    public static void addEntity(UUID entityId) {
        addedEntities.add(entityId);
        if (addedEntities.size() > CONFIG.maxConcurrentAgents.get()) {
            var removedEntityId = addedEntities.poll();
            if (removedEntityId != null) {
                var manager = clients.get(removedEntityId);
                if (manager != null) {
                    manager.close();
                    clients.remove(removedEntityId);
                }
            }
        }
    }

    /**
     * Starts a conversation between a player and a citizen
     *
     * @param player  The player
     * @param citizen The citizen entity
     */
    public static void startConversation(ServerPlayer player, AbstractEntityCitizen citizen) {
        if (CONFIG.geminiApiKey.get().isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("mc_talking.no_key")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        // Set citizen as active entity
        activeEntity.put(playerId, citizen);

        // Create talking manager and add to clients
        clients.put(citizenId, new TalkingManager(citizen, player));
        addEntity(citizenId);

        // Track this conversation pair
        playerConversationPartners.put(playerId, citizenId);
    }

    /**
     * Ends a conversation for a specific player
     *
     * @param playerId    The player's UUID
     * @param sendMessage Whether to send a message to the player
     */
    public static void endConversation(UUID playerId, boolean sendMessage) {
        UUID citizenId = playerConversationPartners.remove(playerId);
        if (citizenId == null) return;

        LivingEntity entity = activeEntity.remove(playerId);
        if (entity != null && entity.isAlive()) {
            ServerPlayer player = entity.level().getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                for (ItemStack item : player.getInventory().items) {
                    if (item.getItem() instanceof CitizenTalkingDevice) {
                        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
                    }
                }

                PacketDistributor.sendToPlayersTrackingEntity(entity, new AiStatusPayload(citizenId, AiStatus.NONE));
                PacketDistributor.sendToPlayer(player, new AiStatusPayload(citizenId, AiStatus.NONE));
                if (sendMessage) {
                    player.sendSystemMessage(Component.translatable("mc_talking.too_far")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        }
    }

    /**
     * Checks if a player is already in a conversation
     *
     * @param playerId The player's UUID
     * @return true if the player is in a conversation, false otherwise
     */
    public static boolean isPlayerInConversation(UUID playerId) {
        return playerConversationPartners.containsKey(playerId);
    }

    /**
     * Gets the entity a player is currently looking at
     *
     * @param playerId The player's UUID
     * @return The UUID of the entity, or null if not looking at any
     */
    public static UUID getPlayerLookTarget(UUID playerId) {
        return playerLookingAt.get(playerId);
    }

    /**
     * Sets the entity a player is currently looking at
     *
     * @param playerId The player's UUID
     * @param entityId The UUID of the entity, or null if not looking at any
     */
    public static void setPlayerLookTarget(UUID playerId, UUID entityId) {
        UUID previousTarget = playerLookingAt.get(playerId);
        if (entityId == null) {
            resetPlayerLookTracking(playerId);
        } else if (previousTarget == null || !previousTarget.equals(entityId)) {
            handleNewTargetEntity(playerId, entityId, previousTarget);
        }
    }

    /**
     * Gets the current look duration for a player
     *
     * @param playerId The player's UUID
     * @return The current look duration in ticks
     */
    public static int getPlayerLookDuration(UUID playerId) {
        return lookDuration.getOrDefault(playerId, 0);
    }

    /**
     * Increments the look duration for a player
     *
     * @param playerId The player's UUID
     */
    public static void incrementLookDuration(UUID playerId) {
        lookDuration.put(playerId, getPlayerLookDuration(playerId) + 1);
    }

    /**
     * Gets the active TalkingManager for an entity
     *
     * @param entityId The UUID of the entity
     * @return The TalkingManager, or null if none exists
     */
    public static TalkingManager getClientForEntity(UUID entityId) {
        return clients.get(entityId);
    }

    /**
     * Gets the active entity for a player
     *
     * @param playerId The player's UUID
     * @return The active entity, or null if none
     */
    public static AbstractEntityCitizen getActiveEntityForPlayer(UUID playerId) {
        return activeEntity.get(playerId);
    }

    public static UUID getPlayerForEntity(UUID entityId) {
        for (Map.Entry<UUID, AbstractEntityCitizen> entry : activeEntity.entrySet()) {
            if (entry.getValue().getUUID().equals(entityId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Gets the active entity for a player (alias for getActiveEntityForPlayer)
     *
     * @param playerId The player's UUID
     * @return The active entity, or null if none
     */
    public static AbstractEntityCitizen getActiveEntity(UUID playerId) {
        return activeEntity.get(playerId);
    }

    /**
     * Gets the citizen a player is conversing with
     *
     * @param playerId The player's UUID
     * @return The UUID of the citizen, or null if not in conversation
     */
    public static UUID getPlayerConversationPartner(UUID playerId) {
        return playerConversationPartners.get(playerId);
    }

    /**
     * Check distances between players and their conversation partners
     * End conversations that exceed the maximum allowed distance
     *
     * @param players List of server players to check
     */
    public static void checkPlayerDistances(List<ServerPlayer> players) {
        // Make a copy to avoid concurrent modification
        Set<UUID> playerIds = new HashSet<>(playerConversationPartners.keySet());

        for (UUID playerId : playerIds) {
            ServerPlayer player = null;

            // Find the player in the server's player list
            for (ServerPlayer serverPlayer : players) {
                if (serverPlayer.getUUID().equals(playerId)) {
                    player = serverPlayer;
                    break;
                }
            }

            if (player == null) {
                // Player not online anymore
                endConversation(playerId, false);
                continue;
            }

            UUID citizenId = playerConversationPartners.get(playerId);
            if (citizenId == null) continue;            // Find the citizen entity
            AbstractEntityCitizen citizen = activeEntity.get(playerId);
            if (citizen == null || !citizen.isAlive()) {
                endConversation(playerId, false);
                continue;
            }
            // Check distance
            double distanceSquared = player.distanceToSqr(citizen);
            if (distanceSquared > (CONFIG.maxConversationDistance.get() * CONFIG.maxConversationDistance.get())) {
                // Too far away, end conversation
                endConversation(playerId, true);
            }
        }
    }

    /**
     * Handles the transition to a new target entity
     *
     * @param playerId         The player's UUID
     * @param currentTargetId  The UUID of the current target
     * @param previousTargetId The UUID of the previous target
     */
    private static void handleNewTargetEntity(UUID playerId, UUID currentTargetId, UUID previousTargetId) {
        long currentTime = System.currentTimeMillis();

        // Check if it's a return to the previous entity within tolerance time
        UUID previousEntityId = previousEntityLookedAt.get(playerId);
        if (previousEntityId != null && previousEntityId.equals(currentTargetId)) {            // If we're returning to the previous entity within tolerance period
            long lastSwitchTime = lastEntitySwitchTime.getOrDefault(playerId, 0L);
            if (currentTime - lastSwitchTime < CONFIG.lookToleranceMs.get()) {
                // We came back to the previous entity quickly, restore previous duration
                // but with a small penalty
                int previousDuration = lookDuration.getOrDefault(playerId, 0);
                lookDuration.put(playerId, Math.max(0, previousDuration - 5));
                playerLookingAt.put(playerId, currentTargetId);
                return;
            }
        }

        // This is a new entity or we exceeded tolerance time
        playerLookingAt.put(playerId, currentTargetId);
        previousEntityLookedAt.put(playerId, previousTargetId);
        lastEntitySwitchTime.put(playerId, currentTime);
        lookDuration.put(playerId, 0);
        activeEntity.remove(playerId);
    }

    /**
     * Reset tracking data when a player stops looking at entities
     *
     * @param playerId Player UUID
     */
    private static void resetPlayerLookTracking(UUID playerId) {
        UUID previousTargetId = playerLookingAt.get(playerId);
        if (previousTargetId != null) {
            previousEntityLookedAt.put(playerId, previousTargetId);
            lastEntitySwitchTime.put(playerId, System.currentTimeMillis());
        }

        playerLookingAt.remove(playerId);
        lookDuration.remove(playerId);
        activeEntity.remove(playerId);
    }

    /**
     * Cleans up all resources when server is stopping
     */
    public static void cleanup() {
        for (TalkingManager client : clients.values()) {
            client.close();
        }
        clients.clear();
        aiStatus.clear();
        activeEntity.clear();
        playerConversationPartners.clear();
        addedEntities.clear();
        playerLookingAt.clear();
        lookDuration.clear();
        previousEntityLookedAt.clear();
        lastEntitySwitchTime.clear();
    }
}
