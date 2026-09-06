package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;

/** Immutable colony broadcast remembered by a citizen. */
public record CitizenBroadcastMemoryView(
        @NotNull String id,
        @NotNull String originatorName,
        @NotNull String message,
        long createdAtMs,
        @NotNull String senderPlayerName
) {
}
