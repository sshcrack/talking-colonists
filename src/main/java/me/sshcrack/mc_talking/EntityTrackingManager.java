package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages tracking of which entities players are looking at
 */
public class EntityTrackingManager {
    // Track which entity each player is looking at
    private final Map<UUID, UUID> playerLookingAt = new HashMap<>();

    // Track how long each player has been looking at an entity (in ticks)
    private final Map<UUID, Integer> lookDuration = new HashMap<>();

    // Track the previous entity that was looked at for tolerance handling
    private final Map<UUID, UUID> previousEntityLookedAt = new HashMap<>();

    // Track when the last entity switch occurred
    private final Map<UUID, Long> lastEntitySwitchTime = new HashMap<>();

    /**
     * Find the entity a player is currently looking at
     *
     * @param player The player to check
     * @return The entity the player is looking at, or null if none
     */
    public AbstractEntityCitizen findEntityPlayerLookingAt(ServerPlayer player) {
        var level = player.level();
        return level.getNearestEntity(
                AbstractEntityCitizen.class,
                TargetingConditions.forNonCombat()
                        .range(McTalkingConfig.activationDistance),
                player,
                player.getX(), player.getY(), player.getZ(),
                player.getBoundingBox().inflate(McTalkingConfig.activationDistance)
        );
    }

    /**
     * Handle when a player starts looking at a new entity
     *
     * @param playerId         Player UUID
     * @param currentTargetId  Current target entity UUID
     * @param previousTargetId Previous target entity UUID
     */
    public void handleNewTargetEntity(UUID playerId, UUID currentTargetId, UUID previousTargetId) {
        long currentTime = System.currentTimeMillis();

        // Check if it's a return to the previous entity within tolerance time
        UUID previousEntityId = previousEntityLookedAt.get(playerId);
        if (previousEntityId != null && previousEntityId.equals(currentTargetId)) {            // If we're returning to the previous entity within tolerance period
            long lastSwitchTime = lastEntitySwitchTime.getOrDefault(playerId, 0L);
            if (currentTime - lastSwitchTime < McTalkingConfig.lookToleranceMs) {
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
    }

    /**
     * Reset tracking data when a player stops looking at entities
     *
     * @param playerId Player UUID
     */
    public void resetPlayerLookTracking(UUID playerId) {
        UUID previousTargetId = playerLookingAt.get(playerId);
        if (previousTargetId != null) {
            previousEntityLookedAt.put(playerId, previousTargetId);
            lastEntitySwitchTime.put(playerId, System.currentTimeMillis());
        }

        playerLookingAt.remove(playerId);
        lookDuration.remove(playerId);
    }

    /**
     * Get the entity a player is looking at
     *
     * @param playerId The player's UUID
     * @return The UUID of the entity the player is looking at, or null if none
     */
    public UUID getEntityPlayerLookingAt(UUID playerId) {
        return playerLookingAt.get(playerId);
    }

    /**
     * Get how long a player has been looking at an entity
     *
     * @param playerId The player's UUID
     * @return The duration in ticks, or 0 if not looking at an entity
     */
    public int getLookDuration(UUID playerId) {
        return lookDuration.getOrDefault(playerId, 0);
    }

    /**
     * Increment the look duration for a player
     *
     * @param playerId The player's UUID
     * @return The new duration
     */
    public int incrementLookDuration(UUID playerId) {
        int currentDuration = lookDuration.getOrDefault(playerId, 0) + 1;
        lookDuration.put(playerId, currentDuration);
        return currentDuration;
    }

    /**
     * Clean up all tracking data
     */
    public void cleanup() {
        playerLookingAt.clear();
        lookDuration.clear();
        previousEntityLookedAt.clear();
        lastEntitySwitchTime.clear();
    }
}
