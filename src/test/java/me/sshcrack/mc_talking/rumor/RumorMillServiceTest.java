package me.sshcrack.mc_talking.rumor;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RumorMillServiceTest {
    @Test
    void consumesFirstHandEventWithoutMutatingItsSnapshotOrLeavingProvenance() {
        var memory = new CitizenMemories();
        memory.addEvent("I finished the bakery.");
        var originalEvents = memory.getEvents();
        var pendingCompaction = memory.snapshotCompaction();

        assertEquals("I finished the bakery.", RumorMillService.takeFirstHandEvent(memory));
        assertEquals(List.of(), memory.getEvents());
        assertEquals(List.of(), memory.getEntries());
        assertEquals(List.of("I finished the bakery."), originalEvents);
        assertThrows(UnsupportedOperationException.class, () -> originalEvents.remove(0));
        assertNull(RumorMillService.takeFirstHandEvent(memory));
        assertFalse(memory.applyCompaction(pendingCompaction, "Old event summary"));
    }

    @Test
    void keepsSecondhandEventsAndUnrelatedFacts() {
        var memory = new CitizenMemories();
        memory.addFact("I am a baker.");
        memory.addEvent("Rumor: The mine flooded.");
        memory.addEvent("I delivered bread.");
        assertEquals("I delivered bread.", RumorMillService.takeFirstHandEvent(memory));
        assertEquals(List.of("Rumor: The mine flooded."), memory.getEvents());
        assertEquals(List.of("I am a baker."), memory.getFacts());
        assertEquals(2, memory.getEntries().size());
        assertNull(RumorMillService.takeFirstHandEvent(memory));
    }

    @Test
    void emptyMemoryHasNoEventToPromote() {
        assertNull(RumorMillService.takeFirstHandEvent(new CitizenMemories()));
    }
}
