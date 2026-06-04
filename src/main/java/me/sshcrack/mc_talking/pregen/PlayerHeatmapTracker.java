package me.sshcrack.mc_talking.pregen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerHeatmapTracker {
    private PlayerHeatmapTracker() {
    }

    public static final int DISTANCE_FOR_RECORDING = 15 * 15;
    private static final Map<CitizenPlayerPair, Long> scores = new ConcurrentHashMap<>();

    public static void recordProximity(UUID citizenId, UUID playerId) {
        CitizenPlayerPair pair = new CitizenPlayerPair(citizenId, playerId);
        scores.compute(pair, (k, v) -> v == null ? 1L : v + 1L);
    }

    public static void decayScores() {
        scores.replaceAll((k, v) -> Math.max(0, v - 1));
        scores.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public static List<CitizenPlayerPair> getTopPairs(int limit) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public record CitizenPlayerPair(UUID citizenId, UUID playerId) {
    }

    public static void removePlayer(UUID playerId) {
        scores.keySet().removeIf(p -> p.playerId().equals(playerId));
    }

    public static void clear() {
        scores.clear();
    }
}
