package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fine-grained typed citizen activity nested under the corresponding stable citizen state. */
public record CitizenSubState(
        @NotNull CitizenAIState state,
        @NotNull MinimalAISubState type,
        @Nullable String context
) {
}
