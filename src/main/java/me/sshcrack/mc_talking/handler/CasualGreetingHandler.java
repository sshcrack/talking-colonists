package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.pregen.PregenerationPlayback;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import me.sshcrack.mc_talking.util.CitizenNeedAssessor;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles casual greetings from content citizens who wave or say hello
 * to nearby players (no urgent needs, no walking to the player).
 */
public class CasualGreetingHandler {
    private CasualGreetingHandler() {
    }

    private static final Map<UUID, Long> lastPlayerCasualGreetingTimes = new ConcurrentHashMap<>();

    public static void checkForCasualGreeting(ServerPlayer player, List<AbstractEntityCitizen> citizens,
                                               Set<UUID> greetedThisInterval) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty())
            return;

        double casualWeight = McTalkingConfig.INSTANCE.instance().citizenCasualGreetingWeight;
        if (casualWeight <= 0)
            return;

        int playerCooldownSecs = McTalkingConfig.INSTANCE.instance().playerUrgentContactCooldownSeconds;
        if (playerCooldownSecs > 0) {
            Long lastContact = lastPlayerCasualGreetingTimes.get(player.getUUID());
            if (lastContact != null && (System.currentTimeMillis() - lastContact) / 1000L < playerCooldownSecs) {
                return;
            }
        }

        double baseChance = McTalkingConfig.INSTANCE.instance().citizenContactBaseChance;

        for (AbstractEntityCitizen citizen : citizens) {
            if (!ConversationManager.canCitizenSpeak(citizen))
                continue;
            if (citizen.getCitizenData() == null)
                continue;
            if (!greetedThisInterval.add(citizen.getUUID()))
                continue;

            if (CitizenNeedAssessor.hasUrgentNeeds(citizen))
                continue;

            if (Math.random() < baseChance * casualWeight) {
                McTalking.LOGGER.info("[CasualGreeting] Citizen {} greeting player {}",
                        citizen.getCitizenData().getName(),
                        player.getName().getString());

                lastPlayerCasualGreetingTimes.put(player.getUUID(), System.currentTimeMillis());

                UUID citizenId = citizen.getUUID();
                UUID playerId = player.getUUID();

                if (PregenerationTaskService.hasPlayerGreeting(citizenId, playerId)
                        && !PregenerationTaskService.isPlayerGreetingOnCooldown(citizenId, playerId)) {
                    var audio = PregenerationTaskService.popPlayerGreeting(citizenId, playerId);
                    if (audio != null && PregenerationPlayback.playAudioIfPossible(citizen, audio)) {
                        PregenerationTaskService.recordPlayerGreetingPlayed(citizenId, playerId);
                    } else if (audio != null) {
                        PregenerationTaskService.putPlayerGreeting(citizenId, playerId, audio);
                    }
                } else {
                    String playerName = player.getName().getString();
                    PregenerationTaskService.generatePlayerGreetingNow(citizen, playerName, audio -> {
                        PregenerationPlayback.playAudioIfPossible(citizen, audio);
                    });
                }
                break;
            }
        }
    }

    public static void onPlayerLeave(UUID playerId) {
        lastPlayerCasualGreetingTimes.remove(playerId);
    }

    public static void onServerStop() {
        lastPlayerCasualGreetingTimes.clear();
    }
}
