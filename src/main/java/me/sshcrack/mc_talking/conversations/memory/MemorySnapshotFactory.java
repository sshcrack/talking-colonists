package me.sshcrack.mc_talking.conversations.memory;

import me.sshcrack.mc_talking.api.memory.CitizenBroadcastMemoryView;
import me.sshcrack.mc_talking.api.memory.CitizenMemorySnapshot;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipView;
import me.sshcrack.mc_talking.api.memory.CitizenRumorMemoryView;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;

/** Single internal conversion from mutable persisted memory into the immutable addon/prompt view. */
public final class MemorySnapshotFactory {
    private MemorySnapshotFactory() {
    }

    public static CitizenMemorySnapshot create(CitizenMemories memories) {
        var relationships = memories.getRelationships().stream()
                .map(r -> new CitizenRelationshipView(r.getTargetUUID(), r.getType(), r.getFactor()))
                .toList();
        var broadcasts = memories.getReceivedBroadcasts().stream()
                .map(b -> new CitizenBroadcastMemoryView(
                        b.getId(), b.getOriginatorName(), b.getMessage(), b.getCreatedAtMs(), b.getSenderPlayerName()))
                .toList();
        var rumors = memories.getReceivedRumors().stream()
                .map(r -> new CitizenRumorMemoryView(r.getId(), r.getOriginatorName(), r.getContent()))
                .toList();
        return new CitizenMemorySnapshot(
                memories.getFacts(),
                memories.getEvents(),
                relationships,
                broadcasts,
                rumors,
                memories.getSummarizedMemory()
        );
    }
}
