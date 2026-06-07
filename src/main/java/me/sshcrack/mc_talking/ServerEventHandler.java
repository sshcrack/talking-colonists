package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.ai.JobStatus;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.conversations.memory.MemoryCompactionService;
import me.sshcrack.mc_talking.pregen.HeatmapTracker;
import me.sshcrack.mc_talking.pregen.PlayerHeatmapTracker;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import me.sshcrack.mc_talking.pregen.PregenerationPlayback;
import me.sshcrack.mc_talking.pregen.DeliveryInteractionManager;
import me.sshcrack.mc_talking.broadcast.BroadcastPropagationService;
import me.sshcrack.mc_talking.rumor.RumorMillService;
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
    private static ServerEventHandler INSTANCE;

    private int tickCounter = 0;

    private record WalkingTarget(UUID playerId, int lastRepathTick) {}
    private final Map<UUID, WalkingTarget> walkingCitizens = new HashMap<>();

    /** playerId → System.currentTimeMillis() of the last urgent contact for that player. */
    private final Map<UUID, Long> lastPlayerUrgentContactTimes = new HashMap<>();

    /** playerId → System.currentTimeMillis() of the last casual greeting for that player. */
    private final Map<UUID, Long> lastPlayerCasualGreetingTimes = new HashMap<>();

    public ServerEventHandler() {
        INSTANCE = this;
    }

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
        MemoryCompactionService.cleanup();
        PregenerationTaskService.cleanup();
        DeliveryInteractionManager.cleanup();
        ConversationManager.cleanup();
        ColonyEventBuffer.clear();
        MinecraftServer server = event.getServer();
        for (UUID citizenId : walkingCitizens.keySet()) {
            abortWalking(citizenId, server);
        }
        walkingCitizens.clear();
        lastPlayerUrgentContactTimes.clear();
        lastPlayerCasualGreetingTimes.clear();
        PlayerHeatmapTracker.clear();
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

            MinecraftServer server = player.getServer();
            walkingCitizens.entrySet().removeIf(entry -> {
                if (entry.getValue().playerId().equals(player.getUUID())) {
                    abortWalking(entry.getKey(), server);
                    return true;
                }
                return false;
            });
            lastPlayerUrgentContactTimes.remove(player.getUUID());
            lastPlayerCasualGreetingTimes.remove(player.getUUID());
            PlayerHeatmapTracker.removePlayer(player.getUUID());
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

        // Collect players currently in a conversation so we can guard against
        // mumbles / random conversations interrupting them.
        var activePlayers = server.getPlayerList().getPlayers();

        // De-duplication sets: ensure each citizen pair / citizen is processed at most
        // once per tick interval, regardless of how many players happen to be nearby.
        Set<Long> processedGreetingPairs = new HashSet<>();  // encoded symmetric pair key
        Set<UUID> mumbledCitizens       = new HashSet<>();   // citizens that mumbled this interval
        Set<UUID> contactedCitizens     = new HashSet<>();   // citizens that made contact this interval
        Set<UUID> greetedCasually       = new HashSet<>();   // citizens that gave a casual greeting this interval

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
                    checkUrgentContactAbort(player);
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

            // Play pregenerated greetings (only when no citizens in range are busy)
            if (doDistanceCheck && McTalkingConfig.INSTANCE.instance().enablePregeneration)
                playPregeneratedGreetings(citizens, processedGreetingPairs);

            // Play pregenerated player greetings (100% trigger if greeting exists)
            if (doDistanceCheck && McTalkingConfig.INSTANCE.instance().enablePlayerGreetingPregen)
                playPregeneratedPlayerGreetings(player, citizens);


            // Mumbling: only if this player is NOT in a conversation
            if (doMumblingCheck) {
                checkForMumblingCitizens(citizens, mumbledCitizens);
            }

            // Citizen-initiated contact: only if this player is NOT already in a conversation
            if (doContactCheck) {
                checkForCitizenInitiatedContact(player, citizens, contactedCitizens);
            }

            // Casual greeting: content citizens occasionally wave/say hello (no walking)
            if (doContactCheck && McTalkingConfig.INSTANCE.instance().citizenCasualGreetingWeight > 0) {
                checkForCasualGreeting(player, citizens, greetedCasually);
            }
        }

        // Walking citizens: update once per tick interval, not per player
        if (doDistanceCheck && McTalkingConfig.INSTANCE.instance().enableUrgentContactWalkToPlayer) {
            updateWalkingCitizens(server);
        }

        // Random citizen-to-citizen conversations (once per interval, not per player)
        if (doRandomConvCheck) {
            checkForRandomConversations(server);
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
                    if (processedPairs.contains(pairKey)) {
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

    private void playPregeneratedGreetings(List<AbstractEntityCitizen> citizens,
                                            Set<Long> processedPairs) {
        int maxPerInterval = McTalkingConfig.INSTANCE.instance().maxGreetingsPerTickInterval;
        int greetingsThisCall = 0;

        for (int i = 0; i < citizens.size(); i++) {
            AbstractEntityCitizen citizenOne = citizens.get(i);
            for (int j = i + 1; j < citizens.size(); j++) {
                AbstractEntityCitizen citizenTwo = citizens.get(j);
                double distSq = citizenOne.distanceToSqr(citizenTwo);

                // Deduplicate: each citizen pair is handled at most once per tick interval,
                // even when multiple players are nearby and trigger this method repeatedly.
                int idA = citizenOne.getId();
                int idB = citizenTwo.getId();
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
                    if (!PregenerationTaskService.isGreetingOnCooldown(citizenOne.getUUID(), citizenTwo.getUUID())) {
                        AudioChunk audio = PregenerationTaskService.peekGreeting(citizenOne.getUUID(), citizenTwo.getUUID());
                        if (audio != null && PregenerationPlayback.playAudioIfPossible(citizenOne, audio)) {
                            PregenerationTaskService.recordGreetingPlayed(citizenOne.getUUID(), citizenTwo.getUUID());
                            PregenerationTaskService.scheduleGreetingRegen(citizenOne, citizenTwo);
                            greetingsThisCall++;
                            if (greetingsThisCall >= maxPerInterval) return;
                        }
                    }
                } else if (PregenerationTaskService.hasGreeting(citizenTwo.getUUID(), citizenOne.getUUID())) {
                    if (!PregenerationTaskService.isGreetingOnCooldown(citizenTwo.getUUID(), citizenOne.getUUID())) {
                        AudioChunk audio = PregenerationTaskService.peekGreeting(citizenTwo.getUUID(), citizenOne.getUUID());
                        if (audio != null && PregenerationPlayback.playAudioIfPossible(citizenTwo, audio)) {
                            PregenerationTaskService.recordGreetingPlayed(citizenTwo.getUUID(), citizenOne.getUUID());
                            PregenerationTaskService.scheduleGreetingRegen(citizenTwo, citizenOne);
                            greetingsThisCall++;
                            if (greetingsThisCall >= maxPerInterval) return;
                        }
                    }
                }
            }
        }
    }

    private void playPregeneratedPlayerGreetings(ServerPlayer player, List<AbstractEntityCitizen> citizens) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;

        UUID playerId = player.getUUID();
        double triggerDist = McTalkingConfig.INSTANCE.instance().playerGreetingDistance;

        for (AbstractEntityCitizen citizen : citizens) {
            if (!ConversationManager.canCitizenSpeak(citizen)) continue;
            if (citizen.getCitizenData() == null) continue;

            double distSq = player.distanceToSqr(citizen);
            if (distSq >= triggerDist * triggerDist) continue;

            UUID citizenId = citizen.getUUID();

            if (PregenerationTaskService.isPlayerGreetingOnCooldown(citizenId, playerId)) continue;

            if (PregenerationTaskService.hasPlayerGreeting(citizenId, playerId)) {
                var audio = PregenerationTaskService.peekPlayerGreeting(citizenId, playerId);
                if (audio != null) {
                    if (PregenerationPlayback.playAudioIfPossible(citizen, audio)) {
                        PregenerationTaskService.recordPlayerGreetingPlayed(citizenId, playerId);
                        PregenerationTaskService.schedulePlayerGreetingRegen(citizen, player);
                    }
                    return;
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

        // Per-player urgent contact cooldown: prevent spam from multiple citizens
        int playerCooldownSecs = McTalkingConfig.INSTANCE.instance().playerUrgentContactCooldownSeconds;
        if (playerCooldownSecs > 0) {
            Long lastContact = lastPlayerUrgentContactTimes.get(player.getUUID());
            if (lastContact != null && (System.currentTimeMillis() - lastContact) / 1000L < playerCooldownSecs) {
                return;
            }
        }

        double baseChance = McTalkingConfig.INSTANCE.instance().citizenContactBaseChance;
        boolean walkToPlayer = McTalkingConfig.INSTANCE.instance().enableUrgentContactWalkToPlayer;

        // When walk-to-player is enabled, scan a wider range for urgent contacts only
        List<AbstractEntityCitizen> contactCitizens = citizens;
        if (walkToPlayer) {
            double wideRange = McTalkingConfig.INSTANCE.instance().urgentContactSearchRange;
            var wideAabb = player.getBoundingBox().inflate(wideRange);
            contactCitizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, wideAabb);
        }

        for (AbstractEntityCitizen citizen : contactCitizens) {
            if (!ConversationManager.canCitizenSpeak(citizen)) continue;
            if (citizen.getCitizenData() == null) continue;
            if (walkingCitizens.containsKey(citizen.getUUID())) continue;
            // Skip citizens that already initiated contact this interval (dedup across players)
            if (!contactedThisInterval.add(citizen.getUUID())) continue;

            double urgencyWeight = calculateUrgencyWeight(citizen);
            if (urgencyWeight <= 0) continue;

            if (Math.random() < baseChance * urgencyWeight) {
                McTalking.LOGGER.info("[CitizenContact] Citizen {} initiating {} with player {}",
                        citizen.getCitizenData().getName(),
                        walkToPlayer ? "walk-to-player" : "contact",
                        player.getName().getString());

                // Record per-player cooldown timestamp
                lastPlayerUrgentContactTimes.put(player.getUUID(), System.currentTimeMillis());

                if (walkToPlayer) {
                    startWalkingUrgentContact(citizen, player);
                } else {
                    ConversationManager.startUrgentContact(citizen, player);
                }
                break; // Only one citizen per player per check
            }
        }
    }

    private void checkForCasualGreeting(ServerPlayer player, List<AbstractEntityCitizen> citizens,
                                         Set<UUID> greetedThisInterval) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty()) return;

        double casualWeight = McTalkingConfig.INSTANCE.instance().citizenCasualGreetingWeight;
        if (casualWeight <= 0) return;

        int playerCooldownSecs = McTalkingConfig.INSTANCE.instance().playerUrgentContactCooldownSeconds;
        if (playerCooldownSecs > 0) {
            Long lastContact = lastPlayerCasualGreetingTimes.get(player.getUUID());
            if (lastContact != null && (System.currentTimeMillis() - lastContact) / 1000L < playerCooldownSecs) {
                return;
            }
        }

        double baseChance = McTalkingConfig.INSTANCE.instance().citizenContactBaseChance;

        for (AbstractEntityCitizen citizen : citizens) {
            if (!ConversationManager.canCitizenSpeak(citizen)) continue;
            if (citizen.getCitizenData() == null) continue;
            if (!greetedThisInterval.add(citizen.getUUID())) continue;

            // Skip citizens with real urgency — handled by the urgent contact check
            if (calculateUrgencyWeight(citizen) > 0) continue;

            if (Math.random() < baseChance * casualWeight) {
                McTalking.LOGGER.info("[CasualGreeting] Citizen {} greeting player {}",
                        citizen.getCitizenData().getName(),
                        player.getName().getString());

                lastPlayerCasualGreetingTimes.put(player.getUUID(), System.currentTimeMillis());

                UUID citizenId = citizen.getUUID();
                UUID playerId = player.getUUID();

                // Try pregenerated greeting first
                if (PregenerationTaskService.hasPlayerGreeting(citizenId, playerId)
                        && !PregenerationTaskService.isPlayerGreetingOnCooldown(citizenId, playerId)) {
                    var audio = PregenerationTaskService.peekPlayerGreeting(citizenId, playerId);
                    if (audio != null && PregenerationPlayback.playAudioIfPossible(citizen, audio)) {
                        PregenerationTaskService.recordPlayerGreetingPlayed(citizenId, playerId);
                        PregenerationTaskService.schedulePlayerGreetingRegen(citizen, player);
                    }
                } else {
                    // Fallback: generate on-demand and play when complete
                    String playerName = player.getName().getString();
                    PregenerationTaskService.generatePlayerGreetingNow(citizen, playerName, audio -> {
                        PregenerationPlayback.playAudioIfPossible(citizen, audio);
                    });
                }
                break;
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

        if (data.getHomeBuilding() == null && !CitizenHelper.isCitizenGuard(citizen)) {
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

        if (data.getJobStatus() == JobStatus.STUCK) {
            weight += McTalkingConfig.INSTANCE.instance().blockingTaskUrgencyMultiplier;
        }

        return weight;
    }

    /**
     * Triggers the walk-to-player urgent contact flow for a citizen targeting a player.
     * Used by the debug command {@code /talking_colonists urgent_contact}.
     */
    public static void triggerWalkToPlayer(AbstractEntityCitizen citizen, ServerPlayer player) {
        if (INSTANCE == null) {
            McTalking.LOGGER.warn("[CitizenContact] ServerEventHandler not initialized");
            return;
        }
        INSTANCE.startWalkingUrgentContact(citizen, player);
    }

    private void startWalkingUrgentContact(AbstractEntityCitizen citizen, ServerPlayer player) {
        if (!ConversationManager.claimSlot(citizen, false)) {
            McTalking.LOGGER.debug("[CitizenContact] No slot available for walking citizen {}", citizen.getUUID());
            return;
        }

        walkingCitizens.put(citizen.getUUID(), new WalkingTarget(player.getUUID(), tickCounter));
        AiStatusHelper.setAiStatusSynced(citizen, AiStatus.URGENT_WALKING);

        citizen.getNavigation().moveTo(player, McTalkingConfig.CITIZEN_URGENT_WALK_SPEED);

        McTalking.LOGGER.info("[CitizenContact] Citizen {} walking to player {}",
                citizen.getCitizenData().getName(), player.getName().getString());
    }

    private void updateWalkingCitizens(MinecraftServer server) {
        var it = walkingCitizens.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID citizenId = entry.getKey();
            WalkingTarget target = entry.getValue();

            var player = server.getPlayerList().getPlayer(target.playerId());
            if (player == null || !player.isAlive()) {
                abortWalking(citizenId, server);
                it.remove();
                continue;
            }

            // Find the citizen entity on the server
            AbstractEntityCitizen citizen = findWalkingCitizenEntity(server, citizenId);
            if (citizen == null || !citizen.isAlive()) {
                abortWalking(citizenId, server);
                it.remove();
                continue;
            }

            // If the player started a conversation with this citizen (e.g. via Talking Device)
            // while they were walking, stop walking and let the conversation take over.
            if (ConversationManager.getPlayerForEntity(citizenId) != null) {
                McTalking.LOGGER.info("[CitizenContact] Citizen {} picked up for player conversation, aborting walk",
                        citizen.getCitizenData().getName());
                citizen.getNavigation().stop();
                AiStatusHelper.setAiStatusSynced(citizen, AiStatus.NONE);
                // Don't release the slot — the player conversation now holds it.
                it.remove();
                continue;
            }

            // Check if urgent need is resolved
            var urgencyData = citizen.getCitizenData();
            if (calculateUrgencyWeight(citizen) <= 0) {
                String citizenName = urgencyData != null ? urgencyData.getName() : "unknown";
                McTalking.LOGGER.info("[CitizenContact] Citizen {} — urgent need resolved, aborting walk",
                        citizenName);
                citizen.getNavigation().stop();
                AiStatusHelper.setAiStatusSynced(citizen, AiStatus.NONE);
                ConversationManager.releaseSlot(citizen);
                it.remove();
                continue;
            }

            // Check if in voice range to start conversation
            // Must be in the same dimension for distance checks to be meaningful
            if (citizen.level() == player.level()) {
                double voiceRange = McTalkingConfig.INSTANCE.instance().citizenInteractionRange;
                if (citizen.distanceToSqr(player) <= voiceRange * voiceRange) {
                    McTalking.LOGGER.info("[CitizenContact] Citizen {} reached player, starting urgent contact",
                            citizen.getCitizenData().getName());
                    it.remove();
                    // Release walking slot so canCitizenSpeak doesn't block the conversation
                    AiStatusHelper.setAiStatusSynced(citizen, AiStatus.NONE);
                    ConversationManager.releaseSlot(citizen);
                    ConversationManager.startUrgentContact(citizen, player);
                    continue;
                }
            }

            // Repath every 20 ticks
            if (tickCounter - target.lastRepathTick >= 20) {
                citizen.getNavigation().moveTo(player, McTalkingConfig.CITIZEN_URGENT_WALK_SPEED);
                entry.setValue(new WalkingTarget(target.playerId(), tickCounter));
            }
        }
    }

    private void abortWalking(UUID citizenId, MinecraftServer server) {
        AbstractEntityCitizen entity = findWalkingCitizenEntity(server, citizenId);
        if (entity != null && entity.isAlive()) {
            entity.getNavigation().stop();
            AiStatusHelper.setAiStatusSynced(entity, AiStatus.NONE);
        }
        ConversationManager.releaseSlot(citizenId);
    }

    @Nullable
    private AbstractEntityCitizen findWalkingCitizenEntity(MinecraftServer server, UUID citizenId) {
        for (var p : server.getPlayerList().getPlayers()) {
            var e = p.serverLevel().getEntities().get(citizenId);
            if (e instanceof AbstractEntityCitizen c) {
                return c;
            }
        }
        return null;
    }

    private void checkUrgentContactAbort(ServerPlayer player) {
        AbstractEntityCitizen citizen = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (citizen == null || citizen.getCitizenData() == null) return;

        // Only auto-abort conversations that originated from the urgent-contact
        // walk-to-player system — not player-initiated chats via the Talking Device.
        if (!ConversationManager.isUrgentConversation(citizen.getUUID())) return;

        if (calculateUrgencyWeight(citizen) <= 0) {
            McTalking.LOGGER.info("[CitizenContact] Urgent need resolved during conversation for citizen {}",
                    citizen.getCitizenData().getName());
            ConversationManager.endConversation(player.getUUID(), false);
        }
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

        double range = McTalkingConfig.INSTANCE.instance().citizenInteractionRange * 2;

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
