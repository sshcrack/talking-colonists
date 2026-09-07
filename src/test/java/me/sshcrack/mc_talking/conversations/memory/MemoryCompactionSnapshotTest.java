package me.sshcrack.mc_talking.conversations.memory;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryCompactionSnapshotTest {
    @Test
    void repeatedCompactionCarriesHistoryAndRetainsConcurrentDuplicateFacts() {
        var memories = new CitizenMemories();
        memories.setSummarizedMemory("I founded the bakery.");
        memories.addFact("I bake bread.");
        var snapshot = memories.snapshotCompaction();
        String prompt = MemoryCompactionPrompt.render("Alex", snapshot);
        assertTrue(prompt.contains("I founded the bakery."));
        assertTrue(prompt.contains("I bake bread."));
        memories.addFact("I bake bread.");
        memories.addEvent("The player brought flour.");
        assertTrue(memories.applyCompaction(snapshot, "I founded the bakery and bake bread."));
        assertEquals(List.of("I bake bread."), memories.getFacts());
        assertEquals(List.of("The player brought flour."), memories.getEvents());
        assertEquals(2, memories.getEntries().size());
        assertTrue(MemoryCompactionPrompt.render("Alex", memories.snapshotCompaction())
                .contains("I founded the bakery and bake bread."));
        assertFalse(memories.applyCompaction(snapshot, "duplicate late completion"));
    }

    @Test
    void removalOrReplacementInvalidatesOldProviderResults() {
        var memories = new CitizenMemories();
        memories.addFact("outdated fact");
        var snapshot = memories.snapshotCompaction();
        memories.removeFact("outdated fact");
        memories.addFact("outdated fact");
        assertFalse(memories.applyCompaction(snapshot, "must not resurrect removed knowledge"));
        assertEquals(List.of("outdated fact"), memories.getFacts());
        assertEquals("", memories.getSummarizedMemory());
    }
}
