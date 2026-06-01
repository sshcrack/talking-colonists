package me.sshcrack.mc_talking.pregen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeatmapTracker {
    private HeatmapTracker() {
        /* This utility class should not be instantiated */
    }

    public static final int DISTANCE_BETWEEN_CITIZENS_FOR_RECORDING = 10 * 10;
    private static final Map<UUIDPair, Long> scores = new ConcurrentHashMap<>();

    public static void recordProximity(UUID uuid1, UUID uuid2) {
        UUIDPair pair = new UUIDPair(uuid1, uuid2);
        scores.compute(pair, (k, v) -> v == null ? 1L : v + 1L);
    }

    public static void decayScores() {
        scores.replaceAll((k, v) -> Math.max(0, v - 1));
        scores.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    public static List<UUIDPair> getTopPairs(int limit) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    public record UUIDPair(UUID id1, UUID id2) {
        public UUIDPair {
            if (id1.compareTo(id2) > 0) {
                // swap to ensure consistent ordering
                var tmp = id1;
                id1 = id2;
                id2 = tmp;
            }
        }
    }
}
