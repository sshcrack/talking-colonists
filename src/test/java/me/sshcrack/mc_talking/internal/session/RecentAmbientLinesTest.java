package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentAmbientLinesTest {
    @Test
    void newestFirstWithoutTheSpeakersOwnLines() {
        var lines = new RecentAmbientLines();
        lines.record("c1", "Anna", "The granary is empty again.", 0);
        lines.record("c1", "Bob", "My feet hurt.", 1_000);
        lines.record("c1", "Carl", "Another cold night.", 2_000);
        lines.record("c2", "Dora", "Other colony.", 2_000);

        var recent = lines.recent("c1", "Bob", 3_000);
        assertEquals(List.of("Carl", "Anna"), recent.stream().map(RecentAmbientLines.Line::speaker).toList());
    }

    @Test
    void oldLinesExpireAndTheLogIsBounded() {
        var lines = new RecentAmbientLines();
        lines.record("c1", "Anna", "Old news.", 0);
        for (int i = 0; i < RecentAmbientLines.MAX_LINES; i++) {
            lines.record("c1", "B" + i, "Line " + i, RecentAmbientLines.WINDOW_MILLIS);
        }
        var recent = lines.recent("c1", "nobody", RecentAmbientLines.WINDOW_MILLIS + 1);
        assertEquals(RecentAmbientLines.MAX_LINES, recent.size());
        assertTrue(recent.stream().noneMatch(line -> line.speaker().equals("Anna")));
    }

    @Test
    void blankLinesAreIgnoredAndLongOnesTrimmed() {
        var lines = new RecentAmbientLines();
        lines.record("c1", "Anna", "   ", 0);
        lines.record("c1", "Bob", "x".repeat(500), 0);
        var recent = lines.recent("c1", "nobody", 0);
        assertEquals(1, recent.size());
        assertEquals(RecentAmbientLines.MAX_CHARS, recent.get(0).text().length());
    }

    @Test
    void promptSectionListsLinesOrIsEmpty() {
        assertEquals("", RecentAmbientLines.promptSection(List.of()));
        String section = RecentAmbientLines.promptSection(
                List.of(new RecentAmbientLines.Line("Anna", "The granary is empty.", 0)));
        assertTrue(section.contains("- Anna: \"The granary is empty.\""));
        assertTrue(section.contains("Do not repeat"));
    }
}
