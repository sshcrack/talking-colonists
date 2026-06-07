package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.util.CitizenHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Tracks which greeting cache keys are currently being regenerated.
     * Key format is {@code "greeterUUID:greetedUUID"}. Prevents duplicate
     * concurrent regeneration for the same pair. The entry is removed when
     * {@link #replaceGreeting} or {@link #replacePlayerGreeting} is called.
     */
    private static final Set<String> pendingRegen = ConcurrentHashMap.newKeySet();

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
                String key12 = c1.getUUID() + ":" + c2.getUUID();
                boolean needsRegen12 = !hasGreeting(c1.getUUID(), c2.getUUID());
                if (needsRegen12 && !pendingRegen.contains(key12)) {
                    scheduleGreetingRegen(c1, c2);
                    return;
                }

                // Check if we need greeting for c2 -> c1
                String key21 = c2.getUUID() + ":" + c1.getUUID();
                boolean needsRegen21 = !hasGreeting(c2.getUUID(), c1.getUUID());
                if (needsRegen21 && !pendingRegen.contains(key21)) {
                    scheduleGreetingRegen(c2, c1);
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
                    boolean needsPlayerRegen = !hasPlayerGreeting(pair.citizenId(), pair.playerId());
                    if (needsPlayerRegen && !pendingRegen.contains(cacheKey)) {
                        schedulePlayerGreetingRegen(citizen, player);
                        return;
                    }
                }
            }
        }
    }

    private static boolean startPregenerationIfPossible(AbstractEntityCitizen citizen, String prompt, java.util.function.Consumer<AudioChunk> onComplete) {
        if (!McTalkingConfig.hasGeminiApiKey()) {
            return false;
        }

        if (generatingCount.get() >= MAX_CONCURRENT_PREGENS)
            return false;

        if (!ConversationManager.hasFreeCapacity(1))
            return false;

        if (!ConversationManager.claimSlot(citizen, false)) {
            return false;
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
            return false;
        }

        return true;
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

    public static boolean hasGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, AudioChunk> friends = greetingCache.get(citizenId);
        return friends != null && friends.containsKey(friendId);
    }

    /**
     * Returns the cached greeting audio WITHOUT removing it from the cache.
     * The entry stays alive until {@link #replaceGreeting} atomically swaps it.
     */
    public static AudioChunk peekGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, AudioChunk> friends = greetingCache.get(citizenId);
        if (friends == null) return null;
        synchronized (friends) {
            return friends.get(friendId);
        }
    }

    /**
     * Atomically replaces the greeting for (citizenId, friendId) with newAudio,
     * or simply stores it if none existed. Also clears the pendingRegen flag.
     */
    public static void replaceGreeting(UUID citizenId, UUID friendId, AudioChunk newAudio) {
        String regenKey = citizenId + ":" + friendId;
        Map<UUID, AudioChunk> friends = greetingCache.computeIfAbsent(citizenId, k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        synchronized (friends) {
            friends.put(friendId, newAudio);
            int max = McTalkingConfig.INSTANCE.instance().maxPregeneratedGreetingsPerCitizen;
            if (max > 0 && friends.size() > max) {
                Iterator<UUID> it = friends.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
        pendingRegen.remove(regenKey);
    }

    public static boolean hasPlayerGreeting(UUID citizenId, UUID playerId) {
        return playerGreetingCache.containsKey(citizenId + ":" + playerId);
    }

    /**
     * Returns the cached player greeting audio WITHOUT removing it from the cache.
     * The entry stays alive until {@link #replacePlayerGreeting} atomically swaps it.
     */
    public static AudioChunk peekPlayerGreeting(UUID citizenId, UUID playerId) {
        return playerGreetingCache.get(citizenId + ":" + playerId);
    }

    /**
     * Atomically replaces the player greeting for (citizenId, playerId) with newAudio,
     * or simply stores it if none existed. Also clears the pendingRegen flag.
     */
    public static void replacePlayerGreeting(UUID citizenId, UUID playerId, AudioChunk newAudio) {
        playerGreetingCache.put(citizenId + ":" + playerId, newAudio);
        pendingRegen.remove(citizenId + ":" + playerId);
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

    /**
     * Schedules a background regeneration of the citizen-to-citizen greeting.
     * The existing cached greeting (if any) is kept intact until the new one
     * arrives and is atomically swapped in via {@link #replaceGreeting}.
     * No-ops if a regen for this pair is already in flight.
     */
    public static void scheduleGreetingRegen(AbstractEntityCitizen greeter, AbstractEntityCitizen greeted) {
        String key = greeter.getUUID() + ":" + greeted.getUUID();
        if (!pendingRegen.add(key)) return;

        String prompt = "Generate a brief 1-sentence passing greeting for your friend "
            + greeted.getCitizenData().getName() + ".";
        boolean started = startPregenerationIfPossible(greeter, prompt, audio -> {
            replaceGreeting(greeter.getUUID(), greeted.getUUID(), audio);
        });
        if (!started) pendingRegen.remove(key);
    }

    /**
     * Schedules a background regeneration of the citizen-to-player greeting.
     * The existing cached greeting (if any) is kept intact until the new one
     * arrives and is atomically swapped in via {@link #replacePlayerGreeting}.
     * No-ops if a regen for this pair is already in flight.
     */
    public static void schedulePlayerGreetingRegen(AbstractEntityCitizen citizen, ServerPlayer player) {
        String key = citizen.getUUID() + ":" + player.getUUID();
        if (!pendingRegen.add(key)) return;

        String playerName = player.getName().getString();
        String prompt = "Generate a brief 1-sentence greeting for " + playerName
            + " who is approaching you. Greet them by name and say hello, you're happy to see them.";
        boolean started = startPregenerationIfPossible(citizen, prompt, audio -> {
            replacePlayerGreeting(citizen.getUUID(), player.getUUID(), audio);
        });
        if (!started) pendingRegen.remove(key);
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
        pendingRegen.clear();
        PlayerHeatmapTracker.clear();
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

    private static ServerPlayer findPlayer(MinecraftServer server, java.util.UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }
}
