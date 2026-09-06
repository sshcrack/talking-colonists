package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Immutable addon-facing snapshot of citizen memory. */
public record CitizenMemorySnapshot(
        @NotNull List<String> facts,
        @NotNull List<String> events,
        @NotNull String summarizedMemory,
        @NotNull String promptText
) {
    public CitizenMemorySnapshot {
        facts = List.copyOf(facts);
        events = List.copyOf(events);
        summarizedMemory = summarizedMemory == null ? "" : summarizedMemory;
        promptText = promptText == null ? "" : promptText;
    }

    /** Convenience constructor for snapshots that are not tied to a current prompt audience. */
    public CitizenMemorySnapshot(List<String> facts, List<String> events, String summarizedMemory) {
        this(facts, events, summarizedMemory, "");
    }
}
