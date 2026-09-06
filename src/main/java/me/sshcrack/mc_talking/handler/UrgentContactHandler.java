package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import me.sshcrack.mc_talking.util.CitizenHelper;
import me.sshcrack.mc_talking.util.CitizenNeedAssessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles citizens with urgent needs who walk to the player and initiate contact.
 */
public class UrgentContactHandler {
    private UrgentContactHandler() {
    }

    private static final long WALK_TIMEOUT_MS = 60_000L;
    private static final long REPATH_INTERVAL_MS = 1_000L;

    private record WalkingTarget(UUID playerId, long startedAtMs, long lastRepathAtMs) {
    }

    private static final Map<UUID, WalkingTarget> walkingCitizens = new HashMap<>();

    private static final Map<UUID, Long> lastPlayerUrgentContactTimes = new HashMap<>();

    public static void checkForCitizenInitiatedContact(ServerPlayer player,
                                                        List<AbstractEntityCitizen> citizens,
                                                        Set<UUID> contactedThisInterval) {
        if (!McTalkingConfig.hasGeminiApiKey())
            return;

        int playerCooldownSecs = McTalkingConfig.INSTANCE.instance().playerUrgentContactCooldownSeconds;
        if (playerCooldownSecs > 0) {
            Long lastContact = lastPlayerUrgentContactTimes.get(player.getUUID());
            if (lastContact != null && (System.currentTimeMillis() - lastContact) / 1000L < playerCooldownSecs) {
                return;
            }
        }

        double baseChance = McTalkingConfig.INSTANCE.instance().citizenContactBaseChance;
        boolean walkToPlayer = McTalkingConfig.INSTANCE.instance().enableUrgentContactWalkToPlayer;

        List<AbstractEntityCitizen> contactCitizens = citizens;
        if (walkToPlayer) {
            double wideRange = McTalkingConfig.INSTANCE.instance().urgentContactSearchRange;
            var wideAabb = player.getBoundingBox().inflate(wideRange);
            contactCitizens = player.level().getEntitiesOfClass(AbstractEntityCitizen.class, wideAabb);
        }

        for (AbstractEntityCitizen citizen : contactCitizens) {
            if (!ConversationManager.canCitizenSpeak(citizen))
                continue;
            if (citizen.getCitizenData() == null)
                continue;
            if (walkingCitizens.containsKey(citizen.getUUID()))
                continue;
            if (!contactedThisInterval.add(citizen.getUUID()))
                continue;

            double urgencyWeight = CitizenNeedAssessor.calculateUrgencyWeight(citizen);
            if (urgencyWeight <= 0)
                continue;

            if (Math.random() < baseChance * urgencyWeight) {
                McTalking.LOGGER.info("[CitizenContact] Citizen {} initiating {} with player {}",
                        citizen.getCitizenData().getName(),
                        walkToPlayer ? "walk-to-player" : "contact",
                        player.getName().getString());

                boolean started = walkToPlayer
                        ? startWalkingUrgentContact(citizen, player)
                        : ConversationManager.startUrgentContact(citizen, player);
                if (started) {
                    // Only consume the per-player cooldown if an interaction actually
                    // started. A full free-tier slot pool should not suppress retries.
                    lastPlayerUrgentContactTimes.put(player.getUUID(), System.currentTimeMillis());
                    break;
                }
            }
        }
    }

    public static boolean triggerWalkToPlayer(AbstractEntityCitizen citizen, ServerPlayer player) {
        return startWalkingUrgentContact(citizen, player);
    }

    static boolean startWalkingUrgentContact(AbstractEntityCitizen citizen, ServerPlayer player) {
        if (!ConversationManager.claimSlot(citizen, false)) {
            McTalking.LOGGER.debug("[CitizenContact] No slot available for walking citizen {}", citizen.getUUID());
            return false;
        }

        long now = System.currentTimeMillis();
        walkingCitizens.put(citizen.getUUID(), new WalkingTarget(player.getUUID(), now, now));
        AiStatusHelper.setAiStatusSynced(citizen, AiStatus.URGENT_WALKING);
        citizen.getNavigation().moveTo(player, McTalkingConfig.CITIZEN_URGENT_WALK_SPEED);

        McTalking.LOGGER.info("[CitizenContact] Citizen {} walking to player {}",
                citizen.getCitizenData().getName(), player.getName().getString());
        return true;
    }

    public static void updateWalkingCitizens(MinecraftServer server) {
        long now = System.currentTimeMillis();
        var it = walkingCitizens.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID citizenId = entry.getKey();
            WalkingTarget target = entry.getValue();

            if (now - target.startedAtMs() >= WALK_TIMEOUT_MS) {
                McTalking.LOGGER.warn("[CitizenContact] Citizen {} did not reach the player within {}s; aborting walk",
                        citizenId, WALK_TIMEOUT_MS / 1000L);
                abortWalking(citizenId, server);
                it.remove();
                continue;
            }

            var player = server.getPlayerList().getPlayer(target.playerId());
            if (player == null || !player.isAlive()) {
                abortWalking(citizenId, server);
                it.remove();
                continue;
            }

            AbstractEntityCitizen citizen = CitizenHelper.findCitizen(server, citizenId);
            if (citizen == null || !citizen.isAlive()) {
                abortWalking(citizenId, server);
                it.remove();
                continue;
            }

            if (ConversationManager.getPlayerForEntity(citizenId) != null) {
                McTalking.LOGGER.info("[CitizenContact] Citizen {} picked up for player conversation, aborting walk",
                        citizen.getCitizenData().getName());
                citizen.getNavigation().stop();
                // The new player session owns the AI status now; do not overwrite
                // LISTENING/IN_CONVERSATION with NONE from the old walk state.
                it.remove();
                continue;
            }

            var urgencyData = citizen.getCitizenData();
            if (CitizenNeedAssessor.calculateUrgencyWeight(citizen) <= 0) {
                String citizenName = urgencyData != null ? urgencyData.getName() : "unknown";
                McTalking.LOGGER.info("[CitizenContact] Citizen {} — urgent need resolved, aborting walk",
                        citizenName);
                citizen.getNavigation().stop();
                AiStatusHelper.setAiStatusSynced(citizen, AiStatus.NONE);
                ConversationManager.releaseSlot(citizen);
                it.remove();
                continue;
            }

            if (citizen.level() == player.level()) {
                double voiceRange = McTalkingConfig.INSTANCE.instance().citizenInteractionRange;
                if (citizen.distanceToSqr(player) <= voiceRange * voiceRange) {
                    McTalking.LOGGER.info("[CitizenContact] Citizen {} reached player, starting urgent contact",
                            citizen.getCitizenData().getName());
                    it.remove();
                    AiStatusHelper.setAiStatusSynced(citizen, AiStatus.NONE);
                    ConversationManager.releaseSlot(citizen);
                    ConversationManager.startUrgentContact(citizen, player);
                    continue;
                }
            }

            if (now - target.lastRepathAtMs() >= REPATH_INTERVAL_MS) {
                citizen.getNavigation().moveTo(player, McTalkingConfig.CITIZEN_URGENT_WALK_SPEED);
                entry.setValue(new WalkingTarget(target.playerId(), target.startedAtMs(), now));
            }
        }
    }

    public static void abortWalking(UUID citizenId, MinecraftServer server) {
        AbstractEntityCitizen entity = CitizenHelper.findCitizen(server, citizenId);
        if (entity != null && entity.isAlive()) {
            entity.getNavigation().stop();
            AiStatusHelper.setAiStatusSynced(entity, AiStatus.NONE);
        }
        ConversationManager.releaseSlot(citizenId);
    }

    public static void checkUrgentContactAbort(ServerPlayer player) {
        AbstractEntityCitizen citizen = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (citizen == null || citizen.getCitizenData() == null)
            return;

        if (!ConversationManager.isUrgentConversation(citizen.getUUID()))
            return;

        if (CitizenNeedAssessor.calculateUrgencyWeight(citizen) <= 0) {
            McTalking.LOGGER.info("[CitizenContact] Urgent need resolved during conversation for citizen {}",
                    citizen.getCitizenData().getName());
            ConversationManager.endConversation(player.getUUID(), false);
        }
    }

    public static void onPlayerLeave(UUID playerId, MinecraftServer server) {
        walkingCitizens.entrySet().removeIf(entry -> {
            if (entry.getValue().playerId().equals(playerId)) {
                abortWalking(entry.getKey(), server);
                return true;
            }
            return false;
        });
        lastPlayerUrgentContactTimes.remove(playerId);
    }

    public static void onServerStop(MinecraftServer server) {
        for (UUID citizenId : walkingCitizens.keySet()) {
            abortWalking(citizenId, server);
        }
        walkingCitizens.clear();
        lastPlayerUrgentContactTimes.clear();
    }
}
