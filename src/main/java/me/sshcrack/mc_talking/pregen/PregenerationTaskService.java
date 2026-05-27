package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PregenerationTaskService {
    private static boolean isGenerating = false;
    private static int tickCounter = 0;
    private static final Map<UUID, Long> lastThreatPlayTime = new ConcurrentHashMap<>();

    // Caches merged into the service
    // citizen UUID -> (friend UUID -> AudioChunk) - insertion ordered map for eviction
    private static final Map<UUID, Map<UUID, AudioChunk>> greetingCache = new ConcurrentHashMap<>();

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
                if (!hasGreeting(c1.getUUID(), c2.getUUID())) {
                    startPregen(c1, "Generate a brief 1-sentence passing greeting for your friend " + c2.getCitizenData().getName() + ".", (audio) -> {
                        putGreeting(c1.getUUID(), c2.getUUID(), audio);
                        ConversationManager.releaseSpeechClaim(c1.getUUID());
                        isGenerating = false;
                    });
                    return;
                }

                // Check if we need greeting for c2 -> c1
                if (!hasGreeting(c2.getUUID(), c1.getUUID())) {
                    startPregen(c2, "Generate a brief 1-sentence passing greeting for your friend " + c1.getCitizenData().getName() + ".", (audio) -> {
                        putGreeting(c2.getUUID(), c1.getUUID(), audio);
                        ConversationManager.releaseSpeechClaim(c2.getUUID());
                        isGenerating = false;
                    });
                    return;
                }
            }
        }

        // Note: threat audio is intentionally NOT pregenerated. Threat prompts are generated on-demand
        // when an attacker's target changes (so they can include the attacker's name). This avoids caching
        // stale attacker-specific messages and reduces storage of threat clips.
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
        playThreatNow(citizen, null);
    }

    /**
     * Attempts to play a pregenerated threat for the given citizen. If none exists,
     * optionally starts a pregeneration that includes the attacker's name in the prompt.
     */
    public static void playThreatNow(AbstractEntityCitizen citizen, Entity attacker) {
        if (citizen.isSleeping() || ConversationManager.isCitizenBusy(citizen.getUUID())) {
            return;
        }

        long now = System.currentTimeMillis();
        int cfg = McTalkingConfig.INSTANCE.instance().threatPlayCooldownMs;
        long cooldownMs = Math.max(0, cfg);
        Long lastPlay = lastThreatPlayTime.get(citizen.getUUID());
        if (lastPlay != null && cooldownMs > 0 && now - lastPlay < cooldownMs) {
            return;
        }

        // Build attacker-aware prompt
        String attackerName = (attacker != null) ? attacker.getName().getString() : null;
        String prompt;
        if (attackerName != null && !attackerName.isEmpty()) {
            prompt = "Generate a brief 1-sentence panic or cry for help because you are being attacked by " + attackerName + ".";
        } else {
            prompt = "Generate a brief 1-sentence panic or cry for help because you are being attacked by a monster.";
        }

        // Generate on-demand (no caching). On completion, attempt immediate playback and update cooldown.
        startPregen(citizen, prompt, (audio) -> {
            long playedAt = System.currentTimeMillis();
            // Attempt playback
            boolean played = PregenPlayback.playAudio(citizen, audio);
            if (played) {
                lastThreatPlayTime.put(citizen.getUUID(), playedAt);
            }
            // If playback failed, do not cache the audio; just let it go. Release claim is handled in startPregen fallback.
            ConversationManager.releaseSpeechClaim(citizen.getUUID());
            isGenerating = false;
        });
    }

    // Cache management methods
    public static void putGreeting(UUID citizenId, UUID friendId, AudioChunk audioData) {
        Map<UUID, AudioChunk> friends = greetingCache.computeIfAbsent(citizenId, k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        synchronized (friends) {
            friends.put(friendId, audioData);
            int max = McTalkingConfig.INSTANCE.instance().maxPregeneratedGreetingsPerCitizen;
            if (max > 0 && friends.size() > max) {
                Iterator<UUID> it = friends.keySet().iterator();
                if (it.hasNext()) {
                    UUID oldest = it.next();
                    it.remove();
                }
            }
        }
    }

    public static AudioChunk popGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, AudioChunk> friends = greetingCache.get(citizenId);
        if (friends != null) {
            synchronized (friends) {
                AudioChunk data = friends.remove(friendId);
                if (friends.isEmpty()) {
                    greetingCache.remove(citizenId);
                }
                return data;
            }
        }
        return null;
    }

    public static boolean hasGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, AudioChunk> friends = greetingCache.get(citizenId);
        return friends != null && friends.containsKey(friendId);
    }

    public static void cleanup() {
        lastThreatPlayTime.clear();
        isGenerating = false;
        tickCounter = 0;
        greetingCache.clear();
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
