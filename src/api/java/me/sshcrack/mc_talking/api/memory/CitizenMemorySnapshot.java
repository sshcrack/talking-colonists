package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Immutable structured addon-facing snapshot of citizen memory. */
public record CitizenMemorySnapshot(
        @NotNull List<String> facts,
        @NotNull List<String> events,
        @NotNull List<CitizenMemoryEntryView> entries,
        @NotNull List<CitizenRelationshipView> relationships,
        @NotNull List<CitizenRelationshipChangeView> relationshipChanges,
        @NotNull List<CitizenBroadcastMemoryView> broadcasts,
        @NotNull List<CitizenRumorMemoryView> rumors,
        @NotNull String summarizedMemory
) {
    public CitizenMemorySnapshot {
        facts = List.copyOf(facts);
        events = List.copyOf(events);
        entries = List.copyOf(entries);
        relationships = List.copyOf(relationships);
        relationshipChanges = List.copyOf(relationshipChanges);
        broadcasts = List.copyOf(broadcasts);
        rumors = List.copyOf(rumors);
        summarizedMemory = summarizedMemory == null ? "" : summarizedMemory;
    }
}
