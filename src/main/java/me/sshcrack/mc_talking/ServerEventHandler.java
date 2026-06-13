package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.commands.McTalkingDebugCommand;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.MemoryCompactionService;
import me.sshcrack.mc_talking.broadcast.BroadcastPropagationService;
import me.sshcrack.mc_talking.handler.CasualGreetingHandler;
import me.sshcrack.mc_talking.handler.CitizenMumblingHandler;
import me.sshcrack.mc_talking.handler.ConstructionEventTracker;
import me.sshcrack.mc_talking.handler.FrustrationHandler;
import me.sshcrack.mc_talking.handler.PregeneratedGreetingHandler;
import me.sshcrack.mc_talking.handler.RandomConversationHandler;
import me.sshcrack.mc_talking.handler.UrgentContactHandler;
import me.sshcrack.mc_talking.rumor.RumorMillService;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.pregen.DeliveryInteractionManager;
import me.sshcrack.mc_talking.pregen.HeatmapTracker;
import me.sshcrack.mc_talking.pregen.PlayerHeatmapTracker;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import me.sshcrack.mc_talking.util.CitizenHelper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


/*? if neoforge {*/
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/*?}*/
/*? if forge {*/
/*import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

*//*?}*/
/*? if neoforge {*/
/*?}*/

/**
 * Orchestrates server-side events for citizen interactions.
 * Delegates domain logic to focused handler classes.
 */
public class ServerEventHandler {
    private int tickCounter = 0;
    private int frustrationTickCounter = 0;

    public ServerEventHandler() {
    }

    @SubscribeEvent
    public void onServerStart(ServerStartingEvent event) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) {
            McTalking.LOGGER.error("======================");
            McTalking.LOGGER.error("Gemini API key not set. McTalking is disabled.");
            McTalking.LOGGER.error("======================");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        McTalkingDebugCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStop(ServerStoppingEvent event) {
        MemoryCompactionService.cleanup();
        PregenerationTaskService.cleanup();
        DeliveryInteractionManager.cleanup();
        ConversationManager.cleanup();

        MinecraftServer server = event.getServer();
        UrgentContactHandler.onServerStop(server);
        CasualGreetingHandler.onServerStop();
        PlayerHeatmapTracker.clear();
        ConstructionEventTracker.clear();
    }

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

    @SubscribeEvent
    public void onPlayerLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConversationManager.endConversation(player.getUUID(), false);

            MinecraftServer server = player.getServer();
            UrgentContactHandler.onPlayerLeave(player.getUUID(), server);
            CasualGreetingHandler.onPlayerLeave(player.getUUID());
            PlayerHeatmapTracker.removePlayer(player.getUUID());
        }
    }

    private void onServerTickCommon(MinecraftServer server) {
        frustrationTickCounter++;
        if (frustrationTickCounter % McTalkingConfig.INSTANCE.instance().frustrationCheckIntervalTicks == 0) {
            FrustrationHandler.tick(server);
        }

        tickCounter++;

        boolean doDistanceCheck = (tickCounter % 5 == 0);
        boolean doMumblingCheck = (tickCounter % McTalkingConfig.INSTANCE.instance().mumblingCheckIntervalTicks == 0);
        boolean doRandomConvCheck = McTalkingConfig.INSTANCE.instance().enableCitizenToCitizenConversation
                && McTalkingConfig.INSTANCE.instance().enableRandomConversations
                && (tickCounter % McTalkingConfig.INSTANCE.instance().randomConversationCheckIntervalTicks == 0);
        boolean doContactCheck = McTalkingConfig.INSTANCE.instance().enableCitizenInitiatedContact
                && (tickCounter % McTalkingConfig.INSTANCE.instance().citizenContactCheckIntervalTicks == 0);
        boolean doRumorCheck = McTalkingConfig.INSTANCE.instance().enableRumorMill
                && (tickCounter % McTalkingConfig.INSTANCE.instance().rumorMillCheckIntervalTicks == 0);
        boolean doBroadcastPropagation = McTalkingConfig.INSTANCE.instance().enableBroadcastPropagation
                && (tickCounter % McTalkingConfig.INSTANCE.instance().broadcastPropagationIntervalTicks == 0);

        if (!doDistanceCheck && !doMumblingCheck && !doRandomConvCheck && !doContactCheck && !doRumorCheck && !doBroadcastPropagation) {
            return;
        }

        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        var activePlayers = server.getPlayerList().getPlayers();

        Set<Long> processedGreetingPairs = new HashSet<>();
        Set<UUID> mumbledCitizens       = new HashSet<>();
        Set<UUID> contactedCitizens     = new HashSet<>();
        Set<UUID> greetedCasually       = new HashSet<>();

        for (ServerPlayer player : activePlayers) {

            UUID playerId = player.getUUID();
            if (McTalkingVoicechatPlugin.shouldDisableColoniesTicks(player))
                continue;

            double range = McTalkingConfig.INSTANCE.instance().citizenInteractionRange;
            var aabb = player.getBoundingBox().inflate(range);
            var citizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, aabb);

            if (doDistanceCheck) {
                trackCitizenProximityHeatmap(citizens, processedGreetingPairs);
                trackPlayerCitizenHeatmap(player, citizens);
                checkConversationDistance(player);
                if (McTalkingConfig.INSTANCE.instance().enableUrgentContactWalkToPlayer) {
                    UrgentContactHandler.checkUrgentContactAbort(player);
                }
            }

            var anyBusy = citizens.stream().anyMatch(ConversationManager::isCitizenBusy);
            if (anyBusy)
                continue;

            AbstractEntityCitizen talkingCitizen = ConversationManager.getActiveEntityForPlayer(player.getUUID());
            if (talkingCitizen != null
                    && !McTalkingConfig.INSTANCE.instance().continueWorkDuringConversation) {
                talkingCitizen.getLookControl().setLookAt(player, 30f, 30f);
                if (!talkingCitizen.getNavigation().isDone()) {
                    talkingCitizen.getNavigation().stop();
                }
            }

            if (ConversationManager.isPlayerInConversation(playerId))
                continue;

            if (doDistanceCheck && McTalkingConfig.INSTANCE.instance().enablePregeneration)
                PregeneratedGreetingHandler.playPregeneratedGreetings(citizens, processedGreetingPairs);

            if (doDistanceCheck && McTalkingConfig.INSTANCE.instance().enablePlayerGreetingPregen)
                PregeneratedGreetingHandler.playPregeneratedPlayerGreetings(player, citizens);

            if (doMumblingCheck) {
                CitizenMumblingHandler.checkForMumblingCitizens(citizens, mumbledCitizens);
            }

            if (doContactCheck) {
                UrgentContactHandler.checkForCitizenInitiatedContact(player, citizens, contactedCitizens);
            }

            if (doContactCheck && McTalkingConfig.INSTANCE.instance().citizenCasualGreetingWeight > 0) {
                CasualGreetingHandler.checkForCasualGreeting(player, citizens, greetedCasually);
            }
        }

        if (doDistanceCheck && McTalkingConfig.INSTANCE.instance().enableUrgentContactWalkToPlayer) {
            UrgentContactHandler.updateWalkingCitizens(server);
        }

        if (doRandomConvCheck) {
            RandomConversationHandler.checkForRandomConversations(server);
        }

        if (McTalkingConfig.INSTANCE.instance().enablePregeneration) {
            PregenerationTaskService.tick(server);
            DeliveryInteractionManager.tick(server);
        }

        if (McTalkingConfig.INSTANCE.instance().enableMemoryCompaction) {
            MemoryCompactionService.tick(server);
        }

        if (doRumorCheck) {
            RumorMillService.tick(server);
        }

        if (doBroadcastPropagation) {
            BroadcastPropagationService.tick(server);
        }
    }

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

    private void checkConversationDistance(ServerPlayer player) {
        AbstractEntityCitizen activeEntity = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (activeEntity == null || !activeEntity.isAlive()) {
            ConversationManager.endConversation(player.getUUID(), false);
            return;
        }

        double distanceSquared = player.distanceToSqr(activeEntity);
        if (distanceSquared > McTalkingConfig.INSTANCE.instance().maxConversationDistance * McTalkingConfig.INSTANCE.instance().maxConversationDistance) {
            ConversationManager.endConversation(player.getUUID(), true);
        }
    }

    private void trackCitizenProximityHeatmap(List<AbstractEntityCitizen> citizens,
                                               Set<Long> processedPairs) {
        for (int i = 0; i < citizens.size(); i++) {
            AbstractEntityCitizen citizenOne = citizens.get(i);
            for (int j = i + 1; j < citizens.size(); j++) {
                AbstractEntityCitizen citizenTwo = citizens.get(j);
                double distSq = citizenOne.distanceToSqr(citizenTwo);
                if (distSq < HeatmapTracker.DISTANCE_BETWEEN_CITIZENS_FOR_RECORDING) {
                    int idA = citizenOne.getId();
                    int idB = citizenTwo.getId();
                    long pairKey = ((long) Math.min(idA, idB) << 32) | (Math.max(idA, idB) & 0xFFFFFFFFL);
                    if (!processedPairs.add(pairKey)) {
                        continue;
                    }
                    HeatmapTracker.recordProximity(citizenOne.getUUID(), citizenTwo.getUUID());
                }
            }
        }
    }

    private void trackPlayerCitizenHeatmap(ServerPlayer player, List<AbstractEntityCitizen> citizens) {
        UUID playerId = player.getUUID();
        for (AbstractEntityCitizen citizen : citizens) {
            double distSq = player.distanceToSqr(citizen);
            if (distSq < PlayerHeatmapTracker.DISTANCE_FOR_RECORDING) {
                PlayerHeatmapTracker.recordProximity(citizen.getUUID(), playerId);
            }
        }
    }


    @SubscribeEvent
    public void onCitizenTargetChanged(LivingChangeTargetEvent event) {
        if (!McTalkingConfig.INSTANCE.instance().enablePregeneration) return;
        /*? if forge {*/
        /*LivingEntity newTarget = event.getNewTarget();
        *//*?}*/

        /*? if neoforge {*/
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        /*?}*/

        if (newTarget instanceof AbstractEntityCitizen citizen
                && !CitizenHelper.isCitizenGuard(citizen)) {
            PregenerationTaskService.playThreatNow(citizen, event.getEntity());
        }
    }
}
