package me.sshcrack.mc_talking.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks short-lived positive colony events that should temporarily influence
 * citizen prompt mood for a limited number of in-game days.
 */
public final class ColonyMoodEventTracker {
    private ColonyMoodEventTracker() {
    }

    private static final Map<Integer, List<MoodEvent>> colonyEvents = new ConcurrentHashMap<>();

    public static void recordPositiveEvent(int colonyId, String promptLine, int expiresAtDay) {
        if (promptLine == null || promptLine.isBlank()) {
            return;
        }
        colonyEvents.computeIfAbsent(colonyId, ignored -> new ArrayList<>())
                .add(new MoodEvent(promptLine, expiresAtDay));
    }

    public static List<String> getActivePositiveEvents(int colonyId, int currentDay) {
        List<MoodEvent> events = colonyEvents.get(colonyId);
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
            colonyEvents.remove(colonyId);
        }

        return active;
    }

    private record MoodEvent(String promptLine, int expiresAtDay) {
    }
}
