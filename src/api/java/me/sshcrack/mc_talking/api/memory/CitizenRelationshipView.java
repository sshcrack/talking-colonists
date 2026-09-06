package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Immutable relationship-memory entry. A neutral factor is zero. */
public record CitizenRelationshipView(
        @NotNull UUID targetId,
        @NotNull CitizenRelationshipDimension dimension,
        float factor
) {
}
