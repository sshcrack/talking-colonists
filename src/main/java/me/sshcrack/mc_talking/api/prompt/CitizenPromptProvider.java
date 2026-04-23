package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusView;
import org.jetbrains.annotations.NotNull;

/**
 * Pluggable provider for citizen AI prompt generation.
 * Other mods can implement this interface and register their provider
 * through {@link CitizenPromptService#setProvider(CitizenPromptProvider)}.
 */
public interface CitizenPromptProvider {

    /**
     * Builds the initial roleplay/system prompt for a citizen conversation.
     *
      * @param view stable prompt data view
     * @return system prompt text
     */
     String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view);

    /**
     * Formats a citizen status value into text used in prompt updates.
     *
     * @param status stable status data view
     * @return status text
     */
    String formatStatus(CitizenStatusView status);
}
