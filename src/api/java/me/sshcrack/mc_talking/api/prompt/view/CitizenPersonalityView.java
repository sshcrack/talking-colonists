package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

/** Immutable addon-facing personality description used during prompt assembly. */
public record CitizenPersonalityView(
        @NotNull String id,
        @NotNull String promptText,
        boolean custom
) {
    public CitizenPersonalityView {
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (promptText.isBlank()) throw new IllegalArgumentException("promptText must not be blank");
    }
}
