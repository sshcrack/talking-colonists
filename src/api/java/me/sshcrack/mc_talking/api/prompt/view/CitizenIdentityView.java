package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Stable identity/personality facts about the citizen. */
public record CitizenIdentityView(
        @NotNull String name,
        boolean child,
        boolean female,
        boolean guard,
        @Nullable CitizenPersonalityView personality,
        @Nullable String customPersonalityText
) {
}
