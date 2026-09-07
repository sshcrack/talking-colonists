package me.sshcrack.mc_talking.conversations.memory;

import me.sshcrack.mc_talking.api.memory.AddonConfirmedOutcome;
import me.sshcrack.mc_talking.api.memory.AddonMemoryWriteResult;
import me.sshcrack.mc_talking.api.memory.ConfirmedRelationshipChange;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.api.memory.MemoryEntryType;
import me.sshcrack.mc_talking.api.memory.MemoryProvenance;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenMemoriesProvenanceTest {
    @Test
    void duplicateAddonOutcomeDoesNotDuplicateEventOrRelationshipChangeEvenAfterReload() {
        UUID player = UUID.randomUUID();
        var outcome = new AddonConfirmedOutcome(
                "colonist_errands",
                "promise:bread:fulfilled:42",
                "The promised bread was delivered.",
                player,
                List.of("The bread promise is fulfilled."),
                List.of(new ConfirmedRelationshipChange(
                        player, CitizenRelationshipDimension.TRUST, 0.2f))
        );
        CitizenMemories memory = new CitizenMemories();

        assertEquals(AddonMemoryWriteResult.ADDED, memory.addConfirmedOutcome(outcome));
        assertEquals(AddonMemoryWriteResult.DUPLICATE, memory.addConfirmedOutcome(outcome));
        assertEquals(1, memory.getEvents().size());
        assertEquals(List.of("The bread promise is fulfilled."), memory.getFacts());
        assertEquals(2, memory.getEntries().size());
        assertEquals(1, memory.getRelationshipChanges().size());
        assertEquals(0.2f, memory.getRelationships().get(0).getFactor(), 0.0001f);

        CitizenMemories reloaded = new CitizenMemories();
        reloaded.deserializeNbt(memory.serializeNbt());
        assertEquals(AddonMemoryWriteResult.DUPLICATE, reloaded.addConfirmedOutcome(outcome));
        assertEquals(1, reloaded.getEvents().size());
        assertEquals(List.of("The bread promise is fulfilled."), reloaded.getFacts());
        assertEquals(1, reloaded.getRelationshipChanges().size());
        assertEquals(0.2f, reloaded.getRelationships().get(0).getFactor(), 0.0001f);

        assertTrue(reloaded.removeEvent("The promised bread was delivered."));
        assertEquals(AddonMemoryWriteResult.DUPLICATE, reloaded.addConfirmedOutcome(outcome));
        assertEquals(0.2f, reloaded.getRelationships().get(0).getFactor(), 0.0001f);
    }

    @Test
    void provenanceAndStableParticipantIdsSurviveSaveReload() {
        UUID citizen = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        CitizenMemories memory = new CitizenMemories();
        memory.addFact(
                "I said I was worried about the bakery.",
                MemoryProvenance.CITIZEN_STATEMENT,
                citizen,
                null,
                null
        );
        memory.addEvent(
                "The player delivered flour.",
                MemoryProvenance.OBSERVED_EVENT,
                player,
                "mc_talking:test",
                "delivery-7"
        );
        memory.addRelationshipChange(
                player,
                CitizenRelationshipDimension.TRUST,
                0.15f,
                MemoryProvenance.OBSERVED_EVENT,
                player,
                "mc_talking:test",
                "delivery-7"
        );

        CitizenMemories reloaded = new CitizenMemories();
        reloaded.deserializeNbt(memory.serializeNbt());

        assertEquals(2, reloaded.getEntries().size());
        assertEquals(MemoryProvenance.CITIZEN_STATEMENT, reloaded.getEntries().get(0).provenance());
        assertEquals(citizen, reloaded.getEntries().get(0).participantId());
        assertEquals(MemoryProvenance.OBSERVED_EVENT, reloaded.getEntries().get(1).provenance());
        assertEquals(player, reloaded.getEntries().get(1).participantId());
        assertEquals("delivery-7", reloaded.getEntries().get(1).idempotencyId());
        assertEquals(MemoryProvenance.OBSERVED_EVENT, reloaded.getRelationshipChanges().get(0).provenance());
        assertEquals(player, reloaded.getRelationshipChanges().get(0).targetId());
    }

    @Test
    void legacyFactsEventsAndRelationshipAggregateMigrateWithoutLoss() {
        UUID target = UUID.randomUUID();
        CompoundTag old = new CompoundTag();
        ListTag facts = new ListTag();
        facts.add(StringTag.valueOf("old fact"));
        old.put("facts", facts);
        ListTag events = new ListTag();
        events.add(StringTag.valueOf("old event"));
        old.put("events", events);
        CompoundTag relationship = new CompoundTag();
        relationship.putString("targetUUID", target.toString());
        relationship.putString("type", CitizenRelationshipDimension.TRUST.name());
        relationship.putFloat("change", 0.3f);
        ListTag relationships = new ListTag();
        relationships.add(relationship);
        old.put("relationships", relationships);

        CitizenMemories migrated = new CitizenMemories();
        migrated.deserializeNbt(old);

        assertEquals(List.of("old fact"), migrated.getFacts());
        assertEquals(List.of("old event"), migrated.getEvents());
        assertEquals(2, migrated.getEntries().size());
        assertTrue(migrated.getEntries().stream().allMatch(
                e -> e.provenance() == MemoryProvenance.LEGACY_UNATTRIBUTED));
        assertTrue(migrated.getEntries().stream().anyMatch(e -> e.type() == MemoryEntryType.FACT));
        assertTrue(migrated.getEntries().stream().anyMatch(e -> e.type() == MemoryEntryType.EVENT));
        assertEquals(0.3f, migrated.getRelationships().get(0).getFactor(), 0.0001f);
        assertEquals(MemoryProvenance.LEGACY_UNATTRIBUTED,
                migrated.getRelationshipChanges().get(0).provenance());
        assertNull(migrated.getRelationshipChanges().get(0).participantId());

        CitizenMemories secondReload = new CitizenMemories();
        secondReload.deserializeNbt(migrated.serializeNbt());
        assertEquals(migrated.getFacts(), secondReload.getFacts());
        assertEquals(migrated.getEvents(), secondReload.getEvents());
        assertEquals(2, secondReload.getEntries().size());
        assertEquals(1, secondReload.getRelationshipChanges().size());
    }

    @Test
    void compactionRemovesRawFactEventEntriesButKeepsAddonIdempotency() {
        UUID player = UUID.randomUUID();
        CitizenMemories memory = new CitizenMemories();
        var outcome = new AddonConfirmedOutcome(
                "test_addon", "outcome-1", "Observed delivery", player,
                List.of("Delivery completed"),
                List.of(new ConfirmedRelationshipChange(
                        player, CitizenRelationshipDimension.TRUST, 0.1f)));

        assertEquals(AddonMemoryWriteResult.ADDED, memory.addConfirmedOutcome(outcome));
        assertEquals(2, memory.getEntries().size());
        assertTrue(memory.applyCompaction(memory.snapshotCompaction(), "The delivery was completed and remembered."));

        assertTrue(memory.getFacts().isEmpty());
        assertTrue(memory.getEvents().isEmpty());
        assertTrue(memory.getEntries().isEmpty(), "raw provenance entries must not survive compaction");
        assertEquals("The delivery was completed and remembered.", memory.getSummarizedMemory());
        assertEquals(1, memory.getRelationshipChanges().size());
        assertEquals(AddonMemoryWriteResult.DUPLICATE, memory.addConfirmedOutcome(outcome),
                "compaction must not erase the durable addon idempotency journal");
    }

    @Test
    void citizenClaimAboutPromiseRemainsCitizenStatementNotPlayerOrConfirmedOutcome() {
        UUID citizen = UUID.randomUUID();
        CitizenMemories memory = new CitizenMemories();
        memory.addEvent(
                "You promised me bread.",
                MemoryProvenance.CITIZEN_STATEMENT,
                citizen,
                null,
                null
        );

        var entry = memory.getEntries().get(0);
        assertEquals(MemoryProvenance.CITIZEN_STATEMENT, entry.provenance());
        assertEquals(citizen, entry.participantId());
        assertNull(entry.source());
        assertNull(entry.idempotencyId());
    }

    @Test
    void compactionRemovesFactEventProvenanceButKeepsRelationshipAndIdempotencyHistory() {
        UUID player = UUID.randomUUID();
        var outcome = new AddonConfirmedOutcome(
                "colonist_errands",
                "delivery:42",
                "The delivery arrived.",
                player,
                List.of("The bakery has flour again."),
                List.of(new ConfirmedRelationshipChange(
                        player, CitizenRelationshipDimension.TRUST, 0.25f))
        );
        CitizenMemories memory = new CitizenMemories();
        assertEquals(AddonMemoryWriteResult.ADDED, memory.addConfirmedOutcome(outcome));

        assertTrue(memory.applyCompaction(memory.snapshotCompaction(), "I remember the successful bakery delivery."));

        var snapshot = MemorySnapshotFactory.create(memory);
        assertTrue(snapshot.facts().isEmpty());
        assertTrue(snapshot.events().isEmpty());
        assertTrue(snapshot.entries().isEmpty());
        assertEquals("I remember the successful bakery delivery.", snapshot.summarizedMemory());
        assertEquals(1, snapshot.relationships().size());
        assertEquals(1, snapshot.relationshipChanges().size());

        CitizenMemories reloaded = new CitizenMemories();
        reloaded.deserializeNbt(memory.serializeNbt());
        var reloadedSnapshot = MemorySnapshotFactory.create(reloaded);
        assertTrue(reloadedSnapshot.facts().isEmpty());
        assertTrue(reloadedSnapshot.events().isEmpty());
        assertTrue(reloadedSnapshot.entries().isEmpty());
        assertEquals("I remember the successful bakery delivery.", reloadedSnapshot.summarizedMemory());
        assertEquals(1, reloadedSnapshot.relationships().size());
        assertEquals(1, reloadedSnapshot.relationshipChanges().size());
        assertEquals(AddonMemoryWriteResult.DUPLICATE, reloaded.addConfirmedOutcome(outcome),
                "compaction must not forget confirmed-outcome idempotency keys");
    }

}
