package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable colony broadcast remembered by a citizen.
 *
 * @param provenance  {@link MemoryProvenance#PLAYER_STATEMENT} for broadcasts a player asked a citizen
 *                    to spread, {@link MemoryProvenance#ADDON_DIRECT_WRITE} for addon/block sources
 * @param sourceLabel non-player source such as "the notice board", or null for player broadcasts
 * @param expiresAtMs epoch millis after which citizens forget it, or 0 for no expiry
 */
public record CitizenBroadcastMemoryView(
        @NotNull String id,
        @NotNull String originatorName,
        @NotNull String message,
        long createdAtMs,
        @NotNull String senderPlayerName,
        @NotNull MemoryProvenance provenance,
        @Nullable String sourceLabel,
        long expiresAtMs
) {
    /** A player broadcast without expiry (the 2.0 shape). */
    public CitizenBroadcastMemoryView(@NotNull String id, @NotNull String originatorName, @NotNull String message,
                                      long createdAtMs, @NotNull String senderPlayerName) {
        this(id, originatorName, message, createdAtMs, senderPlayerName, MemoryProvenance.PLAYER_STATEMENT, null, 0L);
    }
}
