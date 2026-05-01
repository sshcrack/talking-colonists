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
    String getBasicCitizenInfoPrompt(@NotNull CitizenPromptView view, boolean firstPerson);

    /**
     * Builds the initial roleplay/system prompt for a citizen conversation.
     *
     * @param view stable prompt data view
     * @return system prompt text
     */
    String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view);

    String generateConversationalInfoPrompt(@NotNull CitizenPromptView view);

    /**
     * Formats a citizen status value into text used in prompt updates.
     *
     * @param status stable status data view
     * @return status text
     */
    default String formatStatus(CitizenStatusView status) {
        switch (status.type()) {
            case WORKING:
                return "working";
            case SLEEP:
                return "sleeping";
            case HOUSE:
                return "at home";
            case RAIDED:
                return "on alert (raid)";
            case MOURNING:
                var deceased = String.join(",", status.contextValues());
                return "mourning " + deceased;
            case BAD_WEATHER:
                return "sheltering from bad weather";
            case SICK:
                return "ill and needing care";
            case EAT:
                return "hungry and looking for food";
            case UNKNOWN:
            default:
                break;
        }

        String translationKey = status.translationKey();
        if (translationKey.contains(".")) {
            String[] parts = translationKey.split("\\.");
            return parts[parts.length - 1].toLowerCase().replace('_', ' ');
        }

        return translationKey;
    }

    String generateSystemControlledRoleplayPrompt(CitizenPromptView view);
}
