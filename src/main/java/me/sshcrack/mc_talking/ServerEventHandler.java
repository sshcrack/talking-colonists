package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.commands.ListToolsCommand;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import net.minecraft.ChatFormatting;
/*? if forge {*/
/*import net.minecraft.nbt.CompoundTag;
 *//*?}*/
/*? if neoforge {*/
import net.minecraft.core.component.DataComponents;
        /*?}*/
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.ItemStack;
/*? if neoforge {*/
import net.minecraft.world.item.component.CustomModelData;
        /*?}*/
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
/*? if forge {*/
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/*?}*/

import java.util.UUID;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

/**
 * Handler for server-side events related to player-citizen interactions.
 */
public class ServerEventHandler {

    private static final TargetingConditions TARGETING_CONDITIONS = TargetingConditions.forNonCombat().ignoreLineOfSight();
    private static int tickCounter = 0;

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

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ListToolsCommand.register(event.getDispatcher());
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
    }

    /**
     * Called when an entity leaves the level
     */
    @SubscribeEvent
    public void onPlayerLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConversationManager.endConversation(player.getUUID(), false);
        }
    }

    private void onServerTickCommon(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 5 != 0) {
            return;
        }

        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            UUID citizenId = ConversationManager.getPlayerConversationPartner(playerId);
            if (citizenId != null) {
                checkConversationDistance(player, citizenId);
                continue;
            }

            if (!CONFIG.useTalkingDevice.get() || isHoldingTalkingDevice(player)) {
                processPlayerLooking(player);
            }
        }
    }

    /**
     * Called every server tick
     */
    @SubscribeEvent
            /*? if forge {*/
	/*public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		onServerTickCommon(event.getServer());
	}
	*//*?}*/
            /*? if neoforge {*/
    public void onServerTick(ServerTickEvent.Post event) {
        onServerTickCommon(event.getServer());
    }
    /*?}*/

    private void checkConversationDistance(ServerPlayer player, UUID citizenId) {
        AbstractEntityCitizen activeEntity = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (activeEntity == null || !activeEntity.isAlive()) {
            ConversationManager.endConversation(player.getUUID(), false);
            return;
        }

        double distanceSquared = player.distanceToSqr(activeEntity);
        if (distanceSquared > CONFIG.maxConversationDistance.get() * CONFIG.maxConversationDistance.get()) {
            ConversationManager.endConversation(player.getUUID(), true);
        }
    }

    private void processPlayerLooking(ServerPlayer player) {
        HitResult hitResult = player.pick(20.0, 0.0F, false);
        UUID playerId = player.getUUID();

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity targetEntity = ((EntityHitResult) hitResult).getEntity();

            if (targetEntity instanceof AbstractEntityCitizen citizen) {
                double distance = player.distanceToSqr(targetEntity);

                if (distance <= CONFIG.activationDistance.get() * CONFIG.activationDistance.get()) {
                    processLookingAtCitizen(player, citizen);
                    return;
                }
            }
        }

        ConversationManager.setPlayerLookTarget(playerId, null);
    }

    private void processLookingAtCitizen(ServerPlayer player, AbstractEntityCitizen citizen) {
        UUID playerId = player.getUUID();
        UUID citizenId = citizen.getUUID();

        ConversationManager.setPlayerLookTarget(playerId, citizenId);
        ConversationManager.incrementLookDuration(playerId);

        if (ConversationManager.getPlayerLookDuration(playerId) >= CONFIG.lookDurationTicks.get()) {
            boolean playerIsAlone = isPlayerAlone(player);
            if (playerIsAlone || CONFIG.respondInGroups.get()) {
                ConversationManager.startConversation(player, citizen);

                for (ItemStack item : player.getInventory().items) {
                    if (item.getItem() instanceof CitizenTalkingDevice &&
                            player.getInventory().selected == player.getInventory().findSlotMatchingItem(item)) {
                        /*? if forge {*/
						/*CompoundTag tag = item.getOrCreateTag();
						tag.putInt("CustomModelData", 1);

						*//*?}*/
                        /*? if neoforge {*/
                        item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
                        /*?}*/
                        break;
                    }
                }
            } else {
                player.sendSystemMessage(
                        Component.translatable("mc_talking.group_disabled")
                                .withStyle(ChatFormatting.YELLOW)
                );

                ConversationManager.setPlayerLookTarget(playerId, null);
            }
        }
    }

    private boolean isHoldingTalkingDevice(ServerPlayer player) {
        ItemStack heldItem = player.getMainHandItem();
        return heldItem.getItem() instanceof CitizenTalkingDevice;
    }

    private boolean isPlayerAlone(ServerPlayer player) {
        AABB boundingBox = player.getBoundingBox().inflate(5.0);

        int nearbyPlayers = player.level().getNearbyEntities(
                ServerPlayer.class,
                TARGETING_CONDITIONS,
                player,
                boundingBox
        ).size();

        return nearbyPlayers <= 1;
    }
}
