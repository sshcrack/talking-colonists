package me.sshcrack.mc_talking.pregen;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;

public class PregenAudioCache {
    // citizen UUID -> (friend UUID -> AudioChunk)
    private static final Map<UUID, Map<UUID, AudioChunk>> greetingCache = new ConcurrentHashMap<>();
    
    // citizen UUID -> AudioChunk
    private static final Map<UUID, AudioChunk> threatCache = new ConcurrentHashMap<>();

    public static void putGreeting(UUID citizenId, UUID friendId, AudioChunk audioData) {
        greetingCache.computeIfAbsent(citizenId, k -> new ConcurrentHashMap<>()).put(friendId, audioData);
    }

    public static AudioChunk popGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, AudioChunk> friends = greetingCache.get(citizenId);
        if (friends != null) {
            AudioChunk data = friends.remove(friendId);
            if (friends.isEmpty()) {
                greetingCache.remove(citizenId);
            }
            return data;
        }
        return null;
    }

    public static boolean hasGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, AudioChunk> friends = greetingCache.get(citizenId);
        return friends != null && friends.containsKey(friendId);
    }

    public static void putThreat(UUID citizenId, AudioChunk audioData) {
        threatCache.put(citizenId, audioData);
    }

    public static AudioChunk popThreat(UUID citizenId) {
        return threatCache.remove(citizenId);
    }

    public static boolean hasThreat(UUID citizenId) {
        return threatCache.containsKey(citizenId);
    }
}
