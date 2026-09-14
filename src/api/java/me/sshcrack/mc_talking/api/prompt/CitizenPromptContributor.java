package me.sshcrack.mc_talking.api.prompt;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Composable addon prompt extension. Contributors receive immutable prompt/session snapshots already
 * gathered by Talking Colonists. Callbacks may run off the Minecraft server thread (including during
 * provider setup), so they must not read or mutate the live Minecraft world directly.
 */
@FunctionalInterface
public interface CitizenPromptContributor {
    /**
     * Returns zero or more blocks for the requested prompt surface. Return an empty list when the
     * addon has nothing relevant for this citizen/target.
     */
    @NotNull List<PromptContribution> contribute(@NotNull PromptContributionContext context);
}
