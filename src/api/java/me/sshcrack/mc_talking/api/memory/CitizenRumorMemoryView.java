package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;

/** Immutable rumor remembered by a citizen. */
public record CitizenRumorMemoryView(
        @NotNull String id,
        @NotNull String originatorName,
        @NotNull String content
) {
}
