package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Composable addon prompt extension. Contributors receive the immutable snapshot already gathered
 * by Talking Colonists; they should not read or mutate the Minecraft world from callback threads.
 */
@FunctionalInterface
public interface CitizenPromptContributor {
    /**
     * Returns zero or more blocks for the requested prompt surface. Return an empty list when the
     * addon has nothing relevant for this citizen/target.
     */
    @NotNull List<PromptContribution> contribute(
            @NotNull CitizenPromptView view,
            @NotNull PromptTarget target
    );
}
