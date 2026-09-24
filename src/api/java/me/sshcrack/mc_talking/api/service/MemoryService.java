package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.memory.BroadcastReach;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.api.memory.*;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/** Runtime service for server-owned citizen memory access. */
public interface MemoryService {
    boolean addEvent(@NotNull ICitizenData citizen, @NotNull String event);
    boolean removeEvent(@NotNull ICitizenData citizen, @NotNull String event);
    boolean addFact(@NotNull ICitizenData citizen, @NotNull String fact);
    boolean removeFact(@NotNull ICitizenData citizen, @NotNull String fact);
    boolean addRelationshipChange(@NotNull ICitizenData citizen, @NotNull UUID targetId,
                                  @NotNull CitizenRelationshipDimension dimension, float delta);
    @NotNull AddonMemoryWriteResult confirmOutcome(@NotNull ICitizenData citizen, @NotNull AddonConfirmedOutcome outcome);
    @NotNull Optional<CitizenMemorySnapshot> snapshot(@NotNull ICitizenData citizen);

    /** Roadmap A1. Default for runtimes that predate broadcast publishing; see {@code ApiFeature.BROADCAST_PUBLISHING}. */
    default @NotNull BroadcastPublishResult publishBroadcast(@NotNull IColony colony, @NotNull BroadcastRequest request) {
        throw new UnsupportedOperationException("Broadcast publishing is not implemented by this runtime");
    }

    /** Roadmap A1. Default for runtimes that predate broadcast publishing. */
    default boolean retractBroadcast(@NotNull IColony colony, @NotNull String broadcastId) {
        throw new UnsupportedOperationException("Broadcast publishing is not implemented by this runtime");
    }

    /** Default for runtimes that predate {@code ApiFeature.BROADCAST_REACH}. */
    default @NotNull Optional<BroadcastReach> broadcastReach(@NotNull IColony colony, @NotNull String broadcastId) {
        throw new UnsupportedOperationException("Broadcast reach is not implemented by this runtime");
    }
}
