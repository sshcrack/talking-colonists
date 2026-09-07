package me.sshcrack.mc_talking.api.service;

import com.minecolonies.api.colony.ICitizenData;
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
}
