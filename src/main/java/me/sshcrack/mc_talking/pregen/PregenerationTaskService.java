package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;

import java.util.List;

public class PregenerationTaskService {
    private static boolean isGenerating = false;
    private static int tickCounter = 0;

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 200 != 0) return; // Check every 10 seconds

        HeatmapTracker.decayScores();

        if (isGenerating) return;

        List<HeatmapTracker.UUIDPair> topPairs = HeatmapTracker.getTopPairs(5);
        for (HeatmapTracker.UUIDPair pair : topPairs) {
            AbstractEntityCitizen c1 = findCitizen(server, pair.id1);
            AbstractEntityCitizen c2 = findCitizen(server, pair.id2);

            if (c1 != null && c2 != null) {
                if (ConversationManager.isCitizenBusy(c1.getUUID()) || ConversationManager.isCitizenBusy(c2.getUUID())) {
                    continue;
                }

                // Check if we need greeting for c1 -> c2
                if (!PregenAudioCache.hasGreeting(c1.getUUID(), c2.getUUID())) {
                    startPregen(c1, "Generate a brief 1-sentence passing greeting for your friend " + c2.getCitizenData().getName() + ".", (audio) -> {
                        PregenAudioCache.putGreeting(c1.getUUID(), c2.getUUID(), audio);
                        ConversationManager.releaseSpeechClaim(c1.getUUID());
                        isGenerating = false;
                    });
                    return;
                }

                // Check if we need greeting for c2 -> c1
                if (!PregenAudioCache.hasGreeting(c2.getUUID(), c1.getUUID())) {
                    startPregen(c2, "Generate a brief 1-sentence passing greeting for your friend " + c1.getCitizenData().getName() + ".", (audio) -> {
                        PregenAudioCache.putGreeting(c2.getUUID(), c1.getUUID(), audio);
                        ConversationManager.releaseSpeechClaim(c2.getUUID());
                        isGenerating = false;
                    });
                    return;
                }
            }
        }

        // Threat pregeneration: iterate through all active citizens
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AbstractEntityCitizen citizen) {
                    if (ConversationManager.isCitizenBusy(citizen.getUUID())) {
                        continue;
                    }
                    if (!PregenAudioCache.hasThreat(citizen.getUUID())) {
                        startPregen(citizen, "Generate a brief 1-sentence panic or cry for help because you are being attacked by a monster.", (audio) -> {
                            PregenAudioCache.putThreat(citizen.getUUID(), audio);
                            ConversationManager.releaseSpeechClaim(citizen.getUUID());
                            isGenerating = false;
                        });
                        return;
                    }
                }
            }
        }
    }

    private static void startPregen(AbstractEntityCitizen citizen, String prompt, java.util.function.Consumer<AudioChunk> onComplete) {
        if (!ConversationManager.claimSlot(citizen.getUUID(), false)) {
            return;
        }
        if (!ConversationManager.claimSpeech(citizen.getUUID(), false)) {
            ConversationManager.releaseSlot(citizen.getUUID());
            return;
        }
        isGenerating = true;
        McTalking.LOGGER.info("[Pregeneration] Starting audio pregeneration for citizen {}", citizen.getUUID());

        PregenGeminiClient client = new PregenGeminiClient(citizen, prompt, (audio) -> {
            ConversationManager.releaseSlot(citizen.getUUID());
            onComplete.accept(audio);
        }, () -> {
            isGenerating = false;
            ConversationManager.releaseSpeechClaim(citizen.getUUID());
            ConversationManager.releaseSlot(citizen.getUUID());
        });
        client.connect();
    }

    public static void playThreatNow(AbstractEntityCitizen citizen) {
        if (ConversationManager.isCitizenBusy(citizen.getUUID())) {
            return;
        }

        AudioChunk cachedThreat = PregenAudioCache.popThreat(citizen.getUUID());
        if (cachedThreat != null) {
            if (!PregenPlayback.playAudio(citizen, cachedThreat)) {
                PregenAudioCache.putThreat(citizen.getUUID(), cachedThreat);
            }
            return;
        }

        startPregen(citizen,
                "Generate a brief 1-sentence panic or cry for help because you are being attacked by a monster.",
                (audio) -> {
                    PregenAudioCache.putThreat(citizen.getUUID(), audio);
                    if (!PregenPlayback.playAudio(citizen, audio, true)) {
                        PregenAudioCache.putThreat(citizen.getUUID(), audio);
                        ConversationManager.releaseSpeechClaim(citizen.getUUID());
                    }
                    isGenerating = false;
                });
    }

    private static AbstractEntityCitizen findCitizen(MinecraftServer server, java.util.UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractEntityCitizen citizen) {
                return citizen;
            }
        }
        return null;
    }
}
