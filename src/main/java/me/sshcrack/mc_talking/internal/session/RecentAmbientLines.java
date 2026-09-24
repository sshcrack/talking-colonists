package me.sshcrack.mc_talking.internal.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What citizens of a colony said aloud unprompted in the last few minutes, so the next ambient
 * line can avoid repeating it. Without this, a colony short on food makes five citizens mutter the
 * same complaint in slightly different words.
 */
public final class RecentAmbientLines {
    public record Line(String speaker, String text, long atMillis) {
    }

    static final long WINDOW_MILLIS = 10 * 60 * 1000L;
    static final int MAX_LINES = 8;
    static final int MAX_CHARS = 180;

    private final Map<String, Deque<Line>> byColony = new ConcurrentHashMap<>();

    public void record(String colonyKey, String speaker, String text, long nowMillis) {
        String trimmed = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) return;
        if (trimmed.length() > MAX_CHARS) trimmed = trimmed.substring(0, MAX_CHARS - 1) + "…";
        Deque<Line> lines = byColony.computeIfAbsent(colonyKey, ignored -> new ArrayDeque<>());
        synchronized (lines) {
            lines.addFirst(new Line(speaker, trimmed, nowMillis));
            while (lines.size() > MAX_LINES) lines.removeLast();
        }
    }

    /** Newest first, within the window, without {@code speaker}'s own lines. */
    public List<Line> recent(String colonyKey, String speaker, long nowMillis) {
        Deque<Line> lines = byColony.get(colonyKey);
        if (lines == null) return List.of();
        List<Line> result = new ArrayList<>();
        synchronized (lines) {
            lines.removeIf(line -> nowMillis - line.atMillis() > WINDOW_MILLIS);
            for (Line line : lines) {
                if (!line.speaker().equals(speaker)) result.add(line);
            }
        }
        return result;
    }

    public void clear() {
        byColony.clear();
    }

    /** Prompt section for {@code lines}, or an empty string when there is nothing to avoid. */
    public static String promptSection(List<Line> lines) {
        if (lines.isEmpty()) return "";
        StringBuilder section = new StringBuilder("\n\n## ALREADY SAID NEARBY\n")
                .append("Other citizens said these things aloud in the last few minutes:\n");
        for (Line line : lines) {
            section.append("- ").append(line.speaker()).append(": \"").append(line.text()).append("\"\n");
        }
        section.append("Do not repeat these topics or phrasings. If you share one of these concerns, ")
                .append("add something new to it (react to who said it, or a different detail) instead of ")
                .append("restating it; otherwise talk about something else entirely.");
        return section.toString();
    }
}
