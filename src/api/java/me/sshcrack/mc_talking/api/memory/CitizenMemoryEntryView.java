package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Immutable provenance-aware persistent recollection. Participant identity is UUID-only. */
public record CitizenMemoryEntryView(
        @NotNull MemoryEntryType type,
        @NotNull String content,
        @NotNull MemoryProvenance provenance,
        @Nullable UUID participantId,
        @Nullable String source,
        @Nullable String idempotencyId
) {
}
