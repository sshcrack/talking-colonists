package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.pregen.PregenerationPlayback;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles playback of pregenerated citizen-citizen and citizen-player greetings.
 */
public class PregeneratedGreetingHandler {
    private PregeneratedGreetingHandler() {
    }

    public static void playPregeneratedGreetings(List<AbstractEntityCitizen> citizens,
                                                  Set<Long> processedPairs) {
        for (int i = 0; i < citizens.size(); i++) {
            AbstractEntityCitizen citizenOne = citizens.get(i);
            for (int j = i + 1; j < citizens.size(); j++) {
                AbstractEntityCitizen citizenTwo = citizens.get(j);
                double distSq = citizenOne.distanceToSqr(citizenTwo);

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
                        AudioChunk audio = PregenerationTaskService.popGreeting(citizenOne.getUUID(), citizenTwo.getUUID());
                        if (audio != null) {
                            if (PregenerationPlayback.playAudioIfPossible(citizenOne, audio)) {
                                PregenerationTaskService.recordGreetingPlayed(citizenOne.getUUID(), citizenTwo.getUUID());
                            } else {
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

    public static void playPregeneratedPlayerGreetings(ServerPlayer player, List<AbstractEntityCitizen> citizens) {
        if (McTalkingConfig.INSTANCE.instance().geminiApiKey.isEmpty())
            return;

        UUID playerId = player.getUUID();
        double triggerDist = McTalkingConfig.INSTANCE.instance().playerGreetingDistance;

        for (AbstractEntityCitizen citizen : citizens) {
            if (!ConversationManager.canCitizenSpeak(citizen))
                continue;
            if (citizen.getCitizenData() == null)
                continue;

            double distSq = player.distanceToSqr(citizen);
            if (distSq >= triggerDist * triggerDist)
                continue;

            UUID citizenId = citizen.getUUID();

            if (PregenerationTaskService.isPlayerGreetingOnCooldown(citizenId, playerId))
                continue;

            if (PregenerationTaskService.hasPlayerGreeting(citizenId, playerId)) {
                var audio = PregenerationTaskService.popPlayerGreeting(citizenId, playerId);
                if (audio != null) {
                    if (PregenerationPlayback.playAudioIfPossible(citizen, audio)) {
                        PregenerationTaskService.recordPlayerGreetingPlayed(citizenId, playerId);
                    } else {
                        PregenerationTaskService.putPlayerGreeting(citizenId, playerId, audio);
                    }
                    return;
                }
            }
        }
    }
}
