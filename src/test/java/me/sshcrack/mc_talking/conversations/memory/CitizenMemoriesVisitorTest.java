package me.sshcrack.mc_talking.conversations.memory;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenMemoriesVisitorTest {
    @Test
    void keepNewestBoundsFactsAndEventsAndTheirEntries() {
        CitizenMemories memories = new CitizenMemories();
        for (int i = 0; i < 5; i++) {
            memories.addFact("fact " + i);
            memories.addEvent("event " + i);
        }
        memories.keepNewest(2);

        assertEquals(List.of("fact 3", "fact 4"), memories.getFacts());
        assertEquals(List.of("event 3", "event 4"), memories.getEvents());
        assertEquals(4, memories.getEntries().size(), "entry views follow the trimmed lists");
    }

    @Test
    void visitorDaysCountFromTheFirstDaySeenAndSurviveReload() {
        CitizenMemories memories = new CitizenMemories();
        assertEquals(0, memories.visitorDays(10));
        assertEquals(3, memories.visitorDays(13));

        CitizenMemories reloaded = new CitizenMemories();
        reloaded.deserializeNbt(memories.serializeNbt());
        assertEquals(5, reloaded.visitorDays(15));
    }
}
