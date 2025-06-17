package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.UUID;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Handler for server-side events related to player-citizen interactions.
 */
public class ServerEventHandler {

    private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forNonCombat().ignoreLineOfSight();
    private int tickCounter = 0;

    /**
     * Called when the server starts
     */
    @SubscribeEvent
    public void onServerStart(ServerStartingEvent event) {
        if (!CONFIG.geminiApiKey.get().isEmpty()) {
            return;
        }

        McTalking.LOGGER.error("======================");
        McTalking.LOGGER.error("Gemini API key not set. McTalking is disabled.");
        McTalking.LOGGER.error("======================");
    }

    /**
     * Called when the server stops
     */
    @SubscribeEvent
    public void onServerStop(ServerStoppingEvent event) {
        ConversationManager.cleanup();
    }

    /**
     * Called when an entity joins the level
     */
    @SubscribeEvent
    public void onPlayerJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Reset talking device model when player joins
        for (ItemStack item : player.getInventory().items) {
            if (item.getItem() instanceof CitizenTalkingDevice) {
                item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(0));
            }
        }
    }

    /**
     * Called when an entity leaves the level
     */
    @SubscribeEvent
    public void onPlayerLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // End conversation when player leaves
            ConversationManager.endConversation(player.getUUID(), false);
        }
    }

    /**
     * Called every server tick
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 5 != 0) {
            return; // Only process every 5 ticks for performance
        }

        if (event.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        // Process all players
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            // Check if player is in a conversation
            UUID citizenId = ConversationManager.getPlayerConversationPartner(playerId);
            if (citizenId != null) {
                checkConversationDistance(player, citizenId);
                continue; // Skip look processing if already in conversation
            }

            // Only process looking logic if talking device isn't required or player is holding one
            if (!CONFIG.useTalkingDevice.get() || isHoldingTalkingDevice(player)) {
                processPlayerLooking(player);
            }
        }
    }

    /**
     * Checks if a player is too far from their conversation partner
     */
    private void checkConversationDistance(ServerPlayer player, UUID citizenId) {
        AbstractEntityCitizen activeEntity = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (activeEntity == null || !activeEntity.isAlive()) {
            ConversationManager.endConversation(player.getUUID(), false);
            return;
        }

        // Check distance between player and entity
        double distanceSquared = player.distanceToSqr(activeEntity);
        if (distanceSquared > CONFIG.maxConversationDistance.get() * CONFIG.maxConversationDistance.get()) {
            ConversationManager.endConversation(player.getUUID(), true);
        }
    }

    /**
     * Processes a player looking at entities
     */
    private void processPlayerLooking(ServerPlayer player) {
        HitResult hitResult = player.pick(20.0, 0.0F, false);
        UUID playerId = player.getUUID();

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity targetEntity = ((EntityHitResult) hitResult).getEntity();

            // Check if target is a citizen
            if (targetEntity instanceof AbstractEntityCitizen citizen) {
                double distance = player.distanceToSqr(targetEntity);

                // Check if within activation distance
                if (distance <= CONFIG.activationDistance.get() * CONFIG.activationDistance.get()) {
                    processLookingAtCitizen(player, citizen);
                    return;
                }
            }
        }

        // Not looking at a valid target
        ConversationManager.setPlayerLookTarget(playerId, null);
    }

    /**
     * Processes a player looking at a citizen
     */
    private void processLookingAtCitizen(ServerPlayer player, AbstractEntityCitizen citizen) {
        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        // Update or set looking target
        ConversationManager.setPlayerLookTarget(playerId, citizenId);

        // Increment look duration
        ConversationManager.incrementLookDuration(playerId);

        // Check if look duration exceeds threshold
        if (ConversationManager.getPlayerLookDuration(playerId) >= CONFIG.lookDurationTicks.get()) {
            // Check if player is alone or group conversations are enabled
            boolean playerIsAlone = isPlayerAlone(player);
            if (playerIsAlone || CONFIG.respondInGroups.get()) {
                // Start conversation
                ConversationManager.startConversation(player, citizen);

                // Update talking device model if player is holding one
                for (ItemStack item : player.getInventory().items) {
                    if (item.getItem() instanceof CitizenTalkingDevice && player.getInventory().selected == player.getInventory().findSlotMatchingItem(item)) {
                        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
                        break;
                    }
                }
            } else {
                // Notify player that group conversations are disabled
                player.sendSystemMessage(
                        Component.translatable("mc_talking.group_disabled")
                                .withStyle(ChatFormatting.YELLOW)
                );

                // Reset look duration to prevent spam
                ConversationManager.setPlayerLookTarget(playerId, null);
            }
        }
    }

    /**
     * Checks if a player is holding a talking device
     */
    private boolean isHoldingTalkingDevice(ServerPlayer player) {
        ItemStack heldItem = player.getMainHandItem();
        return heldItem.getItem() instanceof CitizenTalkingDevice;
    }

    /**
     * Checks if a player is alone (no other players nearby)
     */
    private boolean isPlayerAlone(ServerPlayer player) {
        // Create bounding box around player
        AABB boundingBox = player.getBoundingBox().inflate(5.0);

        // Count nearby players
        int nearbyPlayers = player.level().getNearbyEntities(
                ServerPlayer.class,
                TARGETING_CONDITIONS,
                player,
                boundingBox
        ).size();

        // Player is alone if only they are detected
        return nearbyPlayers <= 1;
    }
}
