package me.sshcrack.mc_talking.pregen;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PregenAudioCache {
    // citizen UUID -> (friend UUID -> Audio PCM Bytes)
    private static final Map<UUID, Map<UUID, byte[]>> greetingCache = new ConcurrentHashMap<>();
    
    // citizen UUID -> Audio PCM Bytes
    private static final Map<UUID, byte[]> threatCache = new ConcurrentHashMap<>();

    public static void putGreeting(UUID citizenId, UUID friendId, byte[] audioData) {
        greetingCache.computeIfAbsent(citizenId, k -> new ConcurrentHashMap<>()).put(friendId, audioData);
    }

    public static byte[] popGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, byte[]> friends = greetingCache.get(citizenId);
        if (friends != null) {
            byte[] data = friends.remove(friendId);
            if (friends.isEmpty()) {
                greetingCache.remove(citizenId);
            }
            return data;
        }
        return null;
    }

    public static boolean hasGreeting(UUID citizenId, UUID friendId) {
        Map<UUID, byte[]> friends = greetingCache.get(citizenId);
        return friends != null && friends.containsKey(friendId);
    }

    public static void putThreat(UUID citizenId, byte[] audioData) {
        threatCache.put(citizenId, audioData);
    }

    public static byte[] popThreat(UUID citizenId) {
        return threatCache.remove(citizenId);
    }

    public static boolean hasThreat(UUID citizenId) {
        return threatCache.containsKey(citizenId);
    }
}
