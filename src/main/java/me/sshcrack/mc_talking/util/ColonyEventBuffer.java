package me.sshcrack.mc_talking.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ColonyEventBuffer {

    private ColonyEventBuffer() {}

    public enum EventType {
        RAID,
        CITIZEN_DEATH,
        CITIZEN_BORN,
        CITIZEN_HIRED,
        CITIZEN_RESURRECTED,
        CITIZEN_JOB_CHANGE,
        BUILDING_ADDED,
        BUILDING_REMOVED,
        BUILDING_UPGRADED,
        COLONY_FOUNDED
    }

    public record ColonyEvent(EventType type, String description, long timestampMs) {}

    private static final int MAX_EVENTS = 20;
    private static final Map<Integer, ConcurrentLinkedDeque<ColonyEvent>> events = new ConcurrentHashMap<>();

    private static final Map<Integer, Long> lastRaidEndTime = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> lastRaidLostCitizens = new ConcurrentHashMap<>();

    public static void recordRaid(int colonyId, int lostCitizens) {
        long now = System.currentTimeMillis();
        lastRaidEndTime.put(colonyId, now);
        lastRaidLostCitizens.put(colonyId, lostCitizens);
        recordEvent(colonyId, EventType.RAID, lostCitizens + " citizens lost in raid");
    }

    public static void recordEvent(int colonyId, EventType type, String description) {
        long now = System.currentTimeMillis();
        var buffer = events.computeIfAbsent(colonyId, k -> new ConcurrentLinkedDeque<>());
        buffer.addFirst(new ColonyEvent(type, description, now));
        while (buffer.size() > MAX_EVENTS) {
            buffer.pollLast();
        }
    }

    public static List<ColonyEvent> getRecentEvents(int colonyId, int maxAgeSeconds) {
        var buffer = events.get(colonyId);
        if (buffer == null) return List.of();
        long cutoff = System.currentTimeMillis() - (maxAgeSeconds * 1000L);
        List<ColonyEvent> result = new ArrayList<>();
        for (ColonyEvent event : buffer) {
            if (event.timestampMs() >= cutoff) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static boolean isInTrauma(int colonyId, int durationSeconds) {
        if (durationSeconds <= 0) return false;
        Long lastEnd = lastRaidEndTime.get(colonyId);
        if (lastEnd == null) return false;
        return (System.currentTimeMillis() - lastEnd) < (durationSeconds * 1000L);
    }

    public static long millisSinceRaid(int colonyId) {
        Long lastEnd = lastRaidEndTime.get(colonyId);
        if (lastEnd == null) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastEnd;
    }

    public static int getLostCitizens(int colonyId) {
        return lastRaidLostCitizens.getOrDefault(colonyId, 0);
    }

    /**
     * Removes all stored data for a specific colony.
     * Call this when a colony is deleted to prevent memory leaks.
     *
     * <p>Wired up via {@link me.sshcrack.mc_talking.listener.ColonyEventSubscriber}.</p>
     */
    public static void removeColony(int colonyId) {
        events.remove(colonyId);
        lastRaidEndTime.remove(colonyId);
        lastRaidLostCitizens.remove(colonyId);
    }

    public static void clear() {
        events.clear();
        lastRaidEndTime.clear();
        lastRaidLostCitizens.clear();
    }
}
