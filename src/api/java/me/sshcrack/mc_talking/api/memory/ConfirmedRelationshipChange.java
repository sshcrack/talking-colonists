package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Relationship delta that is part of one addon-confirmed outcome. */
public record ConfirmedRelationshipChange(
        @NotNull UUID targetId,
        @NotNull CitizenRelationshipDimension dimension,
        float delta
) {
    public ConfirmedRelationshipChange {
        if (targetId == null) throw new IllegalArgumentException("targetId must not be null");
        if (dimension == null) throw new IllegalArgumentException("dimension must not be null");
        if (!Float.isFinite(delta) || delta < -1.0f || delta > 1.0f) {
            throw new IllegalArgumentException("delta must be finite and within [-1, 1]");
        }
    }
}
