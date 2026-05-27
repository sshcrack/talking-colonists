package me.sshcrack.mc_talking.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks short-lived positive and negative colony events that should temporarily influence
 * citizen prompt mood for a limited number of in-game days.
 */
public final class ColonyMoodEventTracker {
    private ColonyMoodEventTracker() {
    }

    private static final Map<Integer, List<MoodEvent>> colonyPositiveEvents = new ConcurrentHashMap<>();
    private static final Map<Integer, List<MoodEvent>> colonyNegativeEvents = new ConcurrentHashMap<>();

    public static void recordPositiveEvent(int colonyId, String promptLine, int expiresAtDay) {
        if (promptLine == null || promptLine.isBlank()) {
            return;
        }
        colonyPositiveEvents.computeIfAbsent(colonyId, ignored -> new ArrayList<>())
                .add(new MoodEvent(promptLine, expiresAtDay));
    }

    public static void recordNegativeEvent(int colonyId, String promptLine, int expiresAtDay) {
        if (promptLine == null || promptLine.isBlank()) {
            return;
        }
        colonyNegativeEvents.computeIfAbsent(colonyId, ignored -> new ArrayList<>())
                .add(new MoodEvent(promptLine, expiresAtDay));
    }

    public static List<String> getActivePositiveEvents(int colonyId, int currentDay) {
        return getActiveEvents(colonyPositiveEvents, colonyId, currentDay);
    }

    public static List<String> getActiveNegativeEvents(int colonyId, int currentDay) {
        return getActiveEvents(colonyNegativeEvents, colonyId, currentDay);
    }

    private static List<String> getActiveEvents(Map<Integer, List<MoodEvent>> eventMap, int colonyId, int currentDay) {
        List<MoodEvent> events = eventMap.get(colonyId);
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<String> active = new ArrayList<>();
        Iterator<MoodEvent> iterator = events.iterator();
        while (iterator.hasNext()) {
            MoodEvent event = iterator.next();
            if (currentDay > event.expiresAtDay()) {
                iterator.remove();
                continue;
            }
            active.add(event.promptLine());
        }

        if (events.isEmpty()) {
            eventMap.remove(colonyId);
        }

        return active;
    }

    private record MoodEvent(String promptLine, int expiresAtDay) {
    }
}
