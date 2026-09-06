package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import org.jetbrains.annotations.NotNull;

/**
 * Complete prompt-generation strategy.
 *
 * <p>Most addons should contribute context with {@link CitizenPromptService#registerContributor}
 * instead. Provider replacement exists for integrations that intentionally own the full prompt.
 * The supplied {@link CitizenPromptView} is read-only and implemented by Talking Colonists.</p>
 */
public interface CitizenPromptProvider {
    @NotNull String getBasicCitizenInfoPrompt(@NotNull CitizenPromptView view, boolean firstPerson);

    @NotNull String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view);

    @NotNull String getDetailedCitizenInfoPrompt(@NotNull CitizenPromptView view);

    @NotNull String generateConversationalInfoPrompt(@NotNull CitizenPromptView view);

    /** Uses Talking Colonists' already-normalized human-readable status description by default. */
    default @NotNull String formatStatus(@NotNull CitizenStatusView status) {
        return status.description();
    }

    @NotNull String generateSystemControlledRoleplayPrompt(@NotNull CitizenPromptView view);
}
