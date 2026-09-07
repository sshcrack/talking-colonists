package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Provenance for one contribution to an aggregate relationship value. */
public record CitizenRelationshipChangeView(
        @NotNull UUID targetId,
        @NotNull CitizenRelationshipDimension dimension,
        float delta,
        @NotNull MemoryProvenance provenance,
        @Nullable UUID participantId,
        @Nullable String source,
        @Nullable String idempotencyId
) {
}
