package me.sshcrack.mc_talking.conversations.memory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressMemoriesTest {
    private static ProgressMemories.Situation situation(boolean housed, String job, boolean sick) {
        return new ProgressMemories.Situation(housed, job, sick);
    }

    @Test
    void improvementsBecomeGratefulMemories() {
        List<String> memories = ProgressMemories.improvements(situation(false, null, true), situation(true, "farmer", false), 4);
        assertEquals(3, memories.size());
        assertTrue(memories.get(0).startsWith("Moved into a home of your own on day 4"));
        assertTrue(memories.get(1).startsWith("Was given work as farmer on day 4"));
        assertTrue(memories.get(2).startsWith("Recovered from an illness on day 4"));
    }

    @Test
    void nothingIsRememberedWhenNothingGotBetter() {
        assertEquals(List.of(), ProgressMemories.improvements(situation(true, "farmer", false), situation(true, "farmer", false), 4));
        assertEquals(List.of(), ProgressMemories.improvements(situation(true, "farmer", false), situation(false, null, true), 4),
                "losing a home or job is not progress");
        assertEquals(List.of("Started a new job as baker on day 9."),
                ProgressMemories.improvements(situation(true, "farmer", false), situation(true, "baker", false), 9));
    }
}
