package me.sshcrack.mc_talking.pregen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HeatmapTracker {
    public static final int DISTANCE_BETWEEN_CITIZENS_FOR_RECORDING = 5 * 5;
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
                .collect(Collectors.toList());
    }

    public static class UUIDPair {
        public final UUID id1;
        public final UUID id2;

        public UUIDPair(UUID id1, UUID id2) {
            if (id1.compareTo(id2) < 0) {
                this.id1 = id1;
                this.id2 = id2;
            } else {
                this.id1 = id2;
                this.id2 = id1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UUIDPair uuidPair = (UUIDPair) o;
            return id1.equals(uuidPair.id1) && id2.equals(uuidPair.id2);
        }

        @Override
        public int hashCode() {
            return 31 * id1.hashCode() + id2.hashCode();
        }
    }
}
