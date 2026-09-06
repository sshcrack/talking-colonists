package me.sshcrack.mc_talking.api.prompt;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Composable addon prompt extension. Contributors receive immutable prompt/session snapshots already
 * gathered by Talking Colonists; they should not read or mutate the Minecraft world from callbacks.
 */
@FunctionalInterface
public interface CitizenPromptContributor {
    /**
     * Returns zero or more blocks for the requested prompt surface. Return an empty list when the
     * addon has nothing relevant for this citizen/target.
     */
    @NotNull List<PromptContribution> contribute(@NotNull PromptContributionContext context);
}
