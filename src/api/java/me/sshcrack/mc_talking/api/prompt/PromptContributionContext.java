package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Immutable input passed to one prompt contributor invocation. */
public record PromptContributionContext(
        @NotNull CitizenPromptView view,
        @NotNull PromptTarget target,
        @NotNull PromptSessionContext session
) {
    public PromptContributionContext {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(session, "session");
    }
}
