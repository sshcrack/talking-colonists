package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.visitor.VisitorCitizen;
import me.sshcrack.mc_talking.commands.ListToolsCommand;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
/*? if forge {*/
/*import net.minecraft.nbt.CompoundTag;
 *//*?}*/
/*? if neoforge {*/
import net.minecraft.core.component.DataComponents;
        /*?}*/
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
/*? if neoforge {*/
import net.minecraft.world.item.component.CustomModelData;
        /*?}*/
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

        boolean doDistanceCheck = (tickCounter % 5 == 0);
        boolean doMumblingCheck = (tickCounter % CONFIG.mumblingCheckIntervalTicks.get() == 0);

        if (!doDistanceCheck && !doMumblingCheck) {
            return;
        }

        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            if (doDistanceCheck) {
                UUID citizenId = ConversationManager.getPlayerConversationPartner(playerId);
                if (citizenId != null) {
                    checkConversationDistance(player, citizenId);
                }
            }

            if (doMumblingCheck && !ConversationManager.isPlayerInConversation(playerId)) {
                checkForMumblingCitizens(player);
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

    private void checkForMumblingCitizens(ServerPlayer player) {
        double range = CONFIG.mumblingDetectionRange.get();
        var aabb = player.getBoundingBox().inflate(range);
        var citizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, aabb);

        for (AbstractEntityCitizen citizen : citizens) {
            if (citizen instanceof VisitorCitizen) {
                continue;
            }
            if (ConversationManager.getClientForEntity(citizen.getUUID()) != null) {
                continue;
            }
            if (Math.random() < CONFIG.mumblingChance.get()) {
                ConversationManager.startMumbling(citizen);
                break; // Only trigger one mumbling citizen per player per check
            }
        }
    }
}
