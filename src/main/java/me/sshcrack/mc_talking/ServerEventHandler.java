package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.commands.McTalkingDebugCommand;
import me.sshcrack.mc_talking.conversations.CitizenConversation;
import me.sshcrack.mc_talking.item.CitizenTalkingDevice;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import me.sshcrack.mc_talking.util.CitizenHelper;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.pregen.HeatmapTracker;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import me.sshcrack.mc_talking.pregen.PregenerationPlayback;
import me.sshcrack.mc_talking.pregen.PlayerHeatmapTracker;
import me.sshcrack.mc_talking.pregen.DeliveryInteractionManager;

import me.sshcrack.mc_talking.config.McTalkingConfig;

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
 * Handler for server-side events related to player-citizen interactions.
 */
public class ServerEventHandler {
    private int tickCounter = 0;

    /**
     * Called when the server starts
     */
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

    /**
     * Called when the server stops
     */
    @SubscribeEvent
    public void onServerStop(ServerStoppingEvent event) {
        PregenerationTaskService.cleanup();
        DeliveryInteractionManager.cleanup();
        ConversationManager.cleanup();
        ColonyEventBuffer.clear();
        PlayerHeatmapTracker.cleanup();
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
            PlayerHeatmapTracker.onPlayerRemoved(player.getUUID());
        }
    }

    private void onServerTickCommon(MinecraftServer server) {
        tickCounter++;

        boolean doDistanceCheck = (tickCounter % 5 == 0);
        boolean doMumblingCheck = (tickCounter % McTalkingConfig.INSTANCE.instance().mumblingCheckIntervalTicks == 0);
        boolean doRandomConvCheck = McTalkingConfig.INSTANCE.instance().enableCitizenToCitizenConversation
                && McTalkingConfig.INSTANCE.instance().enableRandomConversations
                && (tickCounter % McTalkingConfig.INSTANCE.instance().randomConversationCheckIntervalTicks == 0);
        boolean doContactCheck = McTalkingConfig.INSTANCE.instance().enableCitizenInitiatedContact
                && (tickCounter % McTalkingConfig.INSTANCE.instance().citizenContactCheckIntervalTicks == 0);

        if (!doDistanceCheck && !doMumblingCheck && !doRandomConvCheck && !doContactCheck) {
            return;
        }

        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        // Collect players currently in a conversation so we can guard against
        // mumbles / random conversations interrupting them.
        var activePlayers = server.getPlayerList().getPlayers();

        // De-duplication sets: ensure each citizen pair / citizen is processed at most
        // once per tick interval, regardless of how many players happen to be nearby.
        Set<Long> processedGreetingPairs = new HashSet<>();  // encoded symmetric pair key
        Set<UUID> mumbledCitizens       = new HashSet<>();   // citizens that mumbled this interval
        Set<UUID> contactedCitizens     = new HashSet<>();   // citizens that made contact this interval

        for (ServerPlayer player : activePlayers) {

            UUID playerId = player.getUUID();
            var conn = McTalkingVoicechatPlugin.vcApi.getConnectionOf(player.getUUID());
            if (conn != null && conn.isDisabled())
                continue;

            double range = McTalkingConfig.INSTANCE.instance().mumblingDetectionRange;
            var aabb = player.getBoundingBox().inflate(range);
            var citizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, aabb);

            // Track player-citizen proximity for heatmap
            for (AbstractEntityCitizen citizen : citizens) {
                PlayerHeatmapTracker.recordProximity(player.getUUID(), citizen.getUUID());
            }

            var anyBusy = citizens.stream().anyMatch(ConversationManager::isCitizenBusy);
            if (anyBusy)
                continue;

            if (doDistanceCheck) {
                checkConversationDistance(player);
            }

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
                checkProximityAndGreetings(citizens, processedGreetingPairs);


            // Mumbling: only if this player is NOT in a conversation
            if (doMumblingCheck) {
                checkForMumblingCitizens(citizens, mumbledCitizens);
            }

            // Citizen-initiated contact: only if this player is NOT already in a conversation
            if (doContactCheck) {
                checkForCitizenInitiatedContact(player, citizens, contactedCitizens);
            }
        }

        // Random citizen-to-citizen conversations (once per interval, not per player)
        if (doRandomConvCheck) {
            checkForRandomConversations(server);
        }

        if (McTalkingConfig.INSTANCE.instance().enablePregeneration) {
            PregenerationTaskService.tick(server);
            DeliveryInteractionManager.tick(server);
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

    private void checkProximityAndGreetings(List<AbstractEntityCitizen> citizens,
                                             Set<Long> processedPairs) {
        for (int i = 0; i < citizens.size(); i++) {
            AbstractEntityCitizen citizenOne = citizens.get(i);
            for (int j = i + 1; j < citizens.size(); j++) {
                AbstractEntityCitizen citizenTwo = citizens.get(j);
                double distSq = citizenOne.distanceToSqr(citizenTwo);
                if (distSq < HeatmapTracker.DISTANCE_BETWEEN_CITIZENS_FOR_RECORDING) {
                    HeatmapTracker.recordProximity(citizenOne.getUUID(), citizenTwo.getUUID());
                }

                // Deduplicate: each citizen pair is handled at most once per tick interval,
                // even when multiple players are nearby and trigger this method repeatedly.
                int idA = citizenOne.getId();
                int idB = citizenTwo.getId();
                // Encode as a single long with smaller id in the high 32 bits
                long pairKey = ((long) Math.min(idA, idB) << 32) | (Math.max(idA, idB) & 0xFFFFFFFFL);
                if (!processedPairs.add(pairKey)) {
                    continue;
                }

                if (!ConversationManager.canCitizenSpeak(citizenOne) || !ConversationManager.canCitizenSpeak(citizenTwo)) {
                    continue;
                }

                double triggerDist = McTalkingConfig.INSTANCE.instance().pregeneratedGreetingDistance;
                if (distSq >= triggerDist * triggerDist)
                    continue;

                if (PregenerationTaskService.hasGreeting(citizenOne.getUUID(), citizenTwo.getUUID())) {
                    // Only play if the pair is not still on cooldown from a recent greeting
                    if (!PregenerationTaskService.isGreetingOnCooldown(citizenOne.getUUID(), citizenTwo.getUUID())) {
                        AudioChunk audio = PregenerationTaskService.popGreeting(citizenOne.getUUID(), citizenTwo.getUUID());
                        if (audio != null) {
                            if (PregenerationPlayback.playAudioIfPossible(citizenOne, audio)) {
                                PregenerationTaskService.recordGreetingPlayed(citizenOne.getUUID(), citizenTwo.getUUID());
                            } else {
                                // put back if playback failed
                                PregenerationTaskService.putGreeting(citizenOne.getUUID(), citizenTwo.getUUID(), audio);
                            }
                        }
                    }
                } else if (PregenerationTaskService.hasGreeting(citizenTwo.getUUID(), citizenOne.getUUID())) {
                    if (!PregenerationTaskService.isGreetingOnCooldown(citizenTwo.getUUID(), citizenOne.getUUID())) {
                        AudioChunk audio = PregenerationTaskService.popGreeting(citizenTwo.getUUID(), citizenOne.getUUID());
                        if (audio != null) {
                            if (PregenerationPlayback.playAudioIfPossible(citizenTwo, audio)) {
                                PregenerationTaskService.recordGreetingPlayed(citizenTwo.getUUID(), citizenOne.getUUID());
                            } else {
                                PregenerationTaskService.putGreeting(citizenTwo.getUUID(), citizenOne.getUUID(), audio);
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkForMumblingCitizens(List<AbstractEntityCitizen> citizens,
                                            Set<UUID> mumbledThisInterval) {
        for (AbstractEntityCitizen citizen : citizens) {
            // Skip if this citizen already has any kind of active session
            if (!ConversationManager.canCitizenSpeak(citizen)) continue;
            // Skip if this citizen already mumbled this interval (dedup across multiple players)
            if (!mumbledThisInterval.add(citizen.getUUID())) continue;
            // Skip if this citizen is still within their post-session cooldown
            if (Math.random() < McTalkingConfig.INSTANCE.instance().mumblingChance) {
                ConversationManager.startMumbling(citizen);
                break; // Only trigger one mumbling citizen per player per check
            }
        }
    }

    /**
     * Checks nearby citizens for urgent needs and, based on a weighted random roll, has them
     * proactively speak to the given player (spatial audio, like mumbling but directed).
     *
     * <p>Only one citizen initiates contact per player per check to avoid audio overlap.</p>
     */
    private void checkForCitizenInitiatedContact(ServerPlayer player, List<AbstractEntityCitizen> citizens,
                                                   Set<UUID> contactedThisInterval) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;
        double baseChance = McTalkingConfig.INSTANCE.instance().citizenContactBaseChance;

        for (AbstractEntityCitizen citizen : citizens) {
            if (!ConversationManager.canCitizenSpeak(citizen)) continue;
            if (citizen.getCitizenData() == null) continue;
            // Skip citizens that already initiated contact this interval (dedup across players)
            if (!contactedThisInterval.add(citizen.getUUID())) continue;

            double urgencyWeight = calculateUrgencyWeight(citizen);
            if (urgencyWeight <= 0) continue;

            if (Math.random() < baseChance * urgencyWeight) {
                McTalking.LOGGER.info("[CitizenContact] Citizen {} initiating contact with player {}",
                        citizen.getCitizenData().getName(), player.getName().getString());
                ConversationManager.startUrgentContact(citizen, player);
                break; // Only one citizen per player per check
            }
        }
    }

    /**
     * Calculates an urgency weight for a citizen based on their current state.
     * A higher weight means the citizen is more likely to initiate contact with a player.
     * Returns 0 if the citizen has no pressing concerns.
     */
    private double calculateUrgencyWeight(AbstractEntityCitizen citizen) {
        var data = citizen.getCitizenData();
        if (data == null) return 0;

        double weight = 0;

        double happiness = data.getCitizenHappinessHandler().getHappiness(data.getColony(), data);
        if (happiness < 3.0) {
            weight += 1.5;
        } else if (happiness < 5.0) {
            weight += 0.6;
        }

        if (data.getCitizenDiseaseHandler().isSick()) {
            weight += 0.8;
        }

        if (data.getHomeBuilding() == null) {
            weight += 0.7;
        }

        double saturation = data.getSaturation();
        if (saturation <= 1) {
            weight += 1.0;
        } else if (saturation <= 3) {
            weight += 0.4;
        }

        var entityOpt = data.getEntity();
        if (entityOpt.isPresent()) {
            double healthPercent = (entityOpt.get().getHealth() / Math.max(1.0, entityOpt.get().getMaxHealth())) * 100.0;
            if (healthPercent < 25.0) {
                weight += 1.0;
            } else if (healthPercent < 50.0) {
                weight += 0.4;
            }
        }

        return weight;
    }

    /**
     * Scans for pairs of nearby citizens that are not currently managed and
     * randomly starts a citizen-to-citizen conversation between them.
     *
     * <p>Only citizens within range of an online player are considered — citizens
     * in unloaded or player-free areas would be inaudible anyway, and scanning the
     * entire world on every check is expensive on large servers.</p>
     *
     * <p>Citizens that are currently talking to a player are excluded to avoid
     * interrupting ongoing player conversations.</p>
     */
    private void checkForRandomConversations(MinecraftServer server) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;

        double range = McTalkingConfig.INSTANCE.instance().mumblingDetectionRange * 2;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var nearbyBox = player.getBoundingBox().inflate(range);
            var citizens = player.serverLevel().getEntitiesOfClass(AbstractEntityCitizen.class, nearbyBox);

            // If ANY citizen in range is already in a session, skip starting a new
            // conversation near this player – overlapping sessions are disruptive.
            boolean anyBusy = citizens.stream()
                    .anyMatch(ConversationManager::isCitizenBusy);
            if (anyBusy) continue;

            for (AbstractEntityCitizen citizen : citizens) {
                if (!ConversationManager.canCitizenSpeak(citizen)) continue;

                if (Math.random() >= McTalkingConfig.INSTANCE.instance().randomConversationChance) continue;

                // Find a nearby partner from the same proximity list (no second query needed)
                List<AbstractEntityCitizen> partners = new ArrayList<>();
                for (AbstractEntityCitizen candidate : citizens) {
                    if (candidate == citizen) continue;
                    if (!ConversationManager.canCitizenSpeak(candidate)) continue;
                    partners.add(candidate);
                }

                if (partners.isEmpty()) continue;

                // Pick a random partner
                AbstractEntityCitizen partner = partners.get((int) (Math.random() * partners.size()));

                McTalking.LOGGER.info("[RandomConv] Starting conversation between {} and {}",
                        citizen.getCitizenData().getName(), partner.getCitizenData().getName());

                var conversation = new CitizenConversation(server, List.of(citizen, partner));
                conversation.setOnStateChanged(newState -> {
                    AiStatus status = switch (newState) {
                        case GENERATING -> AiStatus.THINKING;
                        case PLAYING_AUDIO -> AiStatus.IN_CONVERSATION;
                        case ENDED -> AiStatus.NONE;
                    };
                    AiStatusHelper.setAiStatusSynced(citizen, status);
                    AiStatusHelper.setAiStatusSynced(partner, status);
                });
                conversation.performConversation();

                // Only start one random conversation per check to keep API usage bounded
                return;
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
            // Pass the entity that just changed target (the attacker) so generated prompts can mention it
            PregenerationTaskService.playThreatNow(citizen, event.getEntity());
        }
    }

}
