package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.CitizenHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PregenerationTaskService {
    private static final int MAX_CONCURRENT_PREGENS = 2;
    private static final AtomicInteger generatingCount = new AtomicInteger(0);
    private static int tickCounter = 0;
    private static int decayTickCounter = 0;
    private static final Map<UUID, Long> lastThreatPlayTime = new ConcurrentHashMap<>();

    /**
     * Tracks the last time (System.currentTimeMillis()) a greeting was successfully
     * played for each ordered citizen pair (greeter -> greeted).  Used to enforce a
     * per-pair cooldown so that a dense group of citizens does not fire the same
     * greeting over and over while they remain close to each other.
     * <p>
     * The key is {@code greeterUUID + ":" + greetedUUID}.
     */
    private static final Map<String, Long> lastGreetingPlayTime = new ConcurrentHashMap<>();

    /**
     * How long (ms) before the same citizen pair may greet each other again. Default: 60 s.
     */
    private static final long GREETING_PLAY_COOLDOWN_MS = 60_000L;

    // Caches merged into the service
    // citizen UUID -> (friend UUID -> AudioChunk) - insertion ordered map for eviction
    private static final Map<UUID, Map<UUID, AudioChunk>> greetingCache = new ConcurrentHashMap<>();

    private static final Map<String, Long> lastPlayerGreetingPlayTime = new ConcurrentHashMap<>();
    private static final long PLAYER_GREETING_PLAY_COOLDOWN_MS = 60_000L;
    private static final Map<String, AudioChunk> playerGreetingCache = new ConcurrentHashMap<>();

    public static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 200 != 0) return; // Check every 10 seconds

        decayTickCounter++;
        if (decayTickCounter % 600 == 0) {
            HeatmapTracker.decayScores();
        }

        if (generatingCount.get() >= MAX_CONCURRENT_PREGENS) return;

        List<HeatmapTracker.UUIDPair> topPairs = HeatmapTracker.getTopPairs(5);
        for (HeatmapTracker.UUIDPair pair : topPairs) {
            AbstractEntityCitizen c1 = findCitizen(server, pair.id1());
            AbstractEntityCitizen c2 = findCitizen(server, pair.id2());

            if (c1 != null && c2 != null) {
                if (ConversationManager.isCitizenBusy(c1) || ConversationManager.isCitizenBusy(c2)) {
                    continue;
                }

                // Check if we need greeting for c1 -> c2
                if (!hasGreeting(c1.getUUID(), c2.getUUID())) {
                    startPregenerationIfPossible(c1, "Generate a brief 1-sentence passing greeting for your friend " + c2.getCitizenData().getName() + ".", (audio) -> {
                        putGreeting(c1.getUUID(), c2.getUUID(), audio);
                    });
                    return;
                }

                // Check if we need greeting for c2 -> c1
                if (!hasGreeting(c2.getUUID(), c1.getUUID())) {
                    startPregenerationIfPossible(c2, "Generate a brief 1-sentence passing greeting for your friend " + c1.getCitizenData().getName() + ".", (audio) -> {
                        putGreeting(c2.getUUID(), c1.getUUID(), audio);
                    });
                    return;
                }
            }
        }

        // Player greeting pregen: pregenerate greetings for frequent citizen-player pairs
        if (McTalkingConfig.INSTANCE.instance().enablePlayerGreetingPregen) {
            List<PlayerHeatmapTracker.CitizenPlayerPair> topPlayerPairs = PlayerHeatmapTracker.getTopPairs(5);
            for (PlayerHeatmapTracker.CitizenPlayerPair pair : topPlayerPairs) {
                AbstractEntityCitizen citizen = findCitizen(server, pair.citizenId());
                ServerPlayer player = findPlayer(server, pair.playerId());

                if (citizen != null && player != null) {
                    if (ConversationManager.isCitizenBusy(citizen)) {
                        continue;
                    }

                    String cacheKey = pair.citizenId() + ":" + pair.playerId();
                    if (!playerGreetingCache.containsKey(cacheKey)) {
                        String playerName = player.getName().getString();
                        startPregenerationIfPossible(citizen,
                                "Generate a brief 1-sentence greeting for " + playerName + " who is approaching you. Greet them by name and say hello, you're happy to see them.",
                                (audio) -> putPlayerGreeting(pair.citizenId(), pair.playerId(), audio));
                        return;
                    }
                }
            }
        }
    }

    private static void startPregenerationIfPossible(AbstractEntityCitizen citizen, String prompt, java.util.function.Consumer<AudioChunk> onComplete) {
        if (!McTalkingConfig.hasGeminiApiKey()) {
            return;
        }

        if (generatingCount.get() >= MAX_CONCURRENT_PREGENS)
            return;

        if (!ConversationManager.hasFreeCapacity(1))
            return;

        if (!ConversationManager.claimSlot(citizen, false)) {
            return;
        }

        generatingCount.incrementAndGet();
        McTalking.LOGGER.info("[Pregeneration] Starting audio pregeneration for citizen {}", citizen.getUUID());

        PregenerationGeminiClient client = new PregenerationGeminiClient(citizen, prompt, audio -> {
            ConversationManager.releaseSlot(citizen);
            generatingCount.decrementAndGet();
            onComplete.accept(audio);
        }, () -> {
            generatingCount.decrementAndGet();
            ConversationManager.releaseSlot(citizen);
        });

        try {
            client.connect();
        } catch (Exception e) {
            McTalking.LOGGER.error("[Pregeneration] Failed to connect for citizen {}", citizen.getUUID(), e);
            generatingCount.decrementAndGet();
            ConversationManager.releaseSlot(citizen);
        }
    }

    /**
     * Attempts to play a pregenerated threat for the given citizen. If none exists,
     * optionally starts a pregeneration that includes the attacker's name in the prompt.
     */
    public static void playThreatNow(AbstractEntityCitizen citizen, Entity attacker) {
        if (ConversationManager.isCitizenBusy(citizen)) {
            return;
        }

        if (citizen.getCitizenData() == null)
            return;

        long now = System.currentTimeMillis();
        int cfg = McTalkingConfig.INSTANCE.instance().threatPlayCooldownMs;
        long cooldownMs = Math.max(0, cfg);
        Long lastPlay = lastThreatPlayTime.get(citizen.getUUID());
        if (lastPlay != null && cooldownMs > 0 && now - lastPlay < cooldownMs) {
            return;
        }

        McTalking.LOGGER.info("Generating new threat audio for citizen {}, difference is {}", citizen.getUUID(), lastPlay == null ? "null" : now - lastPlay);
        // Build attacker-aware prompt
        String attackerName = (attacker != null) ? attacker.getName().getString() : null;
        String prompt;
        if (CitizenHelper.isCitizenGuard(citizen)) {
            if (attackerName != null && !attackerName.isEmpty()) {
                prompt = "Generate a brief 1-sentence exclaim because you are fighting a " + attackerName + " as a guard right now and are helping your fellow colonists";
            } else {
                prompt = "Generate a brief 1-sentence exclaim because you are defending your fellow colonists as a guard from a monster";
            }
        } else {
            if (attackerName != null && !attackerName.isEmpty()) {
                prompt = "Generate a brief 1-sentence panic or cry for help because you are being attacked by " + attackerName + ".";
            } else {
                prompt = "Generate a brief 1-sentence panic or cry for help because you are being attacked by a monster.";
            }
        }

        // Generate on-demand (no caching). On completion, attempt immediate playback and update cooldown.
        startPregenerationIfPossible(citizen, prompt, audio -> {
            long playedAt = System.currentTimeMillis();
            // Attempt playback
            boolean played = PregenerationPlayback.playAudioIfPossible(citizen, audio);
            if (played) {
                lastThreatPlayTime.put(citizen.getUUID(), playedAt);
            } else {
                lastThreatPlayTime.remove(citizen.getUUID());
            }
        });
    }

    // Cache management methods

    /**
     * Returns {@code true} if the (greeter, greeted) pair is still within its greeting play cooldown.
     */
    public static boolean isGreetingOnCooldown(UUID greeterId, UUID greetedId) {
        String key = greeterId + ":" + greetedId;
        Long last = lastGreetingPlayTime.get(key);
        return last != null && (System.currentTimeMillis() - last) < GREETING_PLAY_COOLDOWN_MS;
    }

    /**
     * Records that a greeting was just successfully played from greeter to greeted.
     */
    public static void recordGreetingPlayed(UUID greeterId, UUID greetedId) {
        lastGreetingPlayTime.put(greeterId + ":" + greetedId, System.currentTimeMillis());
    }

    public static void putGreeting(UUID citizenId, UUID friendId, AudioChunk audioData) {
        Map<UUID, AudioChunk> friends = greetingCache.computeIfAbsent(citizenId, k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        synchronized (friends) {
            friends.put(friendId, audioData);
            int max = McTalkingConfig.INSTANCE.instance().maxPregeneratedGreetingsPerCitizen;
            if (max > 0 && friends.size() > max) {
                Iterator<UUID> it = friends.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
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

    public static boolean hasPlayerGreeting(UUID citizenId, UUID playerId) {
        return playerGreetingCache.containsKey(citizenId + ":" + playerId);
    }

    public static AudioChunk popPlayerGreeting(UUID citizenId, UUID playerId) {
        return playerGreetingCache.remove(citizenId + ":" + playerId);
    }

    public static void putPlayerGreeting(UUID citizenId, UUID playerId, AudioChunk audioData) {
        playerGreetingCache.put(citizenId + ":" + playerId, audioData);
    }

    public static boolean isPlayerGreetingOnCooldown(UUID citizenId, UUID playerId) {
        String key = citizenId + ":" + playerId;
        Long last = lastPlayerGreetingPlayTime.get(key);
        return last != null && (System.currentTimeMillis() - last) < PLAYER_GREETING_PLAY_COOLDOWN_MS;
    }

    public static void recordPlayerGreetingPlayed(UUID citizenId, UUID playerId) {
        lastPlayerGreetingPlayTime.put(citizenId + ":" + playerId, System.currentTimeMillis());
    }

    public static void generatePlayerGreetingNow(AbstractEntityCitizen citizen, String playerName, java.util.function.Consumer<AudioChunk> onComplete) {
        String prompt = "Generate a brief 1-sentence greeting for " + playerName + " who is approaching you. Greet them by name and say hello, you're happy to see them.";
        startPregenerationIfPossible(citizen, prompt, onComplete);
    }

    public static boolean isPregenerating() {
        return generatingCount.get() > 0;
    }

    public static void cleanup() {
        lastThreatPlayTime.clear();
        generatingCount.set(0);
        tickCounter = 0;
        decayTickCounter = 0;
        lastGreetingPlayTime.clear();
        greetingCache.clear();
        lastPlayerGreetingPlayTime.clear();
        playerGreetingCache.clear();
        PlayerHeatmapTracker.clear();
    }

    private static AbstractEntityCitizen findCitizen(MinecraftServer server, java.util.UUID uuid) {
        return CitizenHelper.findCitizen(server, uuid);
    }

    private static ServerPlayer findPlayer(MinecraftServer server, java.util.UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }
}
