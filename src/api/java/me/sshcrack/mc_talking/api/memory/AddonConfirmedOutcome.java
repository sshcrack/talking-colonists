package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Gameplay outcome an addon has authoritatively confirmed. {@code idempotencyId} is scoped to
 * {@code source}; retrying the same pair is safe and must not duplicate memory or relationship deltas.
 */
public record AddonConfirmedOutcome(
        @NotNull String source,
        @NotNull String idempotencyId,
        @NotNull String event,
        @Nullable UUID participantId,
        @NotNull List<String> facts,
        @NotNull List<ConfirmedRelationshipChange> relationshipChanges
) {
    public AddonConfirmedOutcome {
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (idempotencyId == null || idempotencyId.isBlank()) throw new IllegalArgumentException("idempotencyId must not be blank");
        if (event == null || event.isBlank()) throw new IllegalArgumentException("event must not be blank");
        facts = List.copyOf(facts);
        if (facts.stream().anyMatch(fact -> fact == null || fact.isBlank())) {
            throw new IllegalArgumentException("facts must not contain blank values");
        }
        relationshipChanges = List.copyOf(relationshipChanges);
    }
}
