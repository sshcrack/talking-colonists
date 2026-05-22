package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.PromptProviderPreset;
import org.jetbrains.annotations.NotNull;

public class PresetCitizenPromptProvider extends DefaultCitizenPromptProvider {
    private final PromptProviderPreset preset;

    public PresetCitizenPromptProvider(@NotNull PromptProviderPreset preset) {
        this.preset = preset;
    }

    @Override
    public String getDetailedCitizenInfoPrompt(@NotNull CitizenPromptView view) {
        return transformPrompt(super.getDetailedCitizenInfoPrompt(view));
    }

    @Override
    public String generateConversationalInfoPrompt(@NotNull CitizenPromptView view) {
        return transformPrompt(super.generateConversationalInfoPrompt(view));
    }

    @Override
    public String generateSystemControlledRoleplayPrompt(CitizenPromptView view) {
        return transformPrompt(super.generateSystemControlledRoleplayPrompt(view));
    }

    @Override
    public String generateCitizenRoleplayPrompt(@NotNull CitizenPromptView view) {
        return transformPrompt(super.generateCitizenRoleplayPrompt(view));
    }

    private String transformPrompt(String basePrompt) {
        return switch (preset) {
            case MEDIUM_MADNESS -> mediumMadness(basePrompt);
            case LOW_MADNESS -> lowMadness(basePrompt);
            case FRIENDLY -> friendly(basePrompt);
            case STOIC -> stoic(basePrompt);
            case PRACTICAL -> practical(basePrompt);
            case DEFAULT -> basePrompt;
        };
    }

    private static String mediumMadness(String prompt) {
        return prompt
                .replace("- Deeply unhappy and possibly hostile\n", "- Deeply unhappy and tense\n")
                .replace("- Will openly complain and make demands\n", "- Will complain directly about problems\n")
                .replace("- May refuse requests or be uncooperative\n", "- May be reluctant to cooperate\n")
                .replace("- Miserable (", "- Very unhappy (")
                .replace("SHOULD STRONGLY INFLUENCE", "should clearly influence");
    }

    private static String lowMadness(String prompt) {
        return prompt
                .replace("- Deeply unhappy and possibly hostile\n", "- Deeply unhappy and emotionally exhausted\n")
                .replace("- Will openly complain and make demands\n", "- Will calmly explain urgent concerns\n")
                .replace("- May refuse requests or be uncooperative\n", "- May need reassurance before agreeing\n")
                .replace("- Visibly unhappy and somewhat irritable\n", "- Visibly unhappy but still respectful\n")
                .replace("- Miserable (", "- Unhappy (")
                .replace("- Unhappy (", "- Concerned (")
                .replace("SHOULD STRONGLY INFLUENCE", "should gently influence");
    }

    private static String friendly(String prompt) {
        return prompt
                .replace("- Generally neutral in demeanor\n", "- Warm and approachable in demeanor\n")
                .replace("- Visibly unhappy and somewhat irritable\n", "- Tries to stay polite even when unhappy\n")
                .replace("- Deeply unhappy and possibly hostile\n", "- Deeply unhappy but still avoids hostility\n")
                .replace("- May refuse requests or be uncooperative\n", "- Prefers cooperative solutions where possible\n")
                .replace("SHOULD STRONGLY INFLUENCE", "should influence");
    }

    private static String stoic(String prompt) {
        return prompt
                .replace("- Generally cheerful and friendly\n", "- Reserved and composed\n")
                .replace("- Generally neutral in demeanor\n", "- Calm and restrained in demeanor\n")
                .replace("- Visibly unhappy and somewhat irritable\n", "- Unhappy but controlled in tone\n")
                .replace("- Deeply unhappy and possibly hostile\n", "- Deeply unhappy but emotionally contained\n")
                .replace("make that clear in your tone and content", "show it with measured, restrained language");
    }

    private static String practical(String prompt) {
        return prompt
                .replace("- Generally cheerful and friendly\n", "- Focused on practical day-to-day tasks\n")
                .replace("- Generally neutral in demeanor\n", "- Direct and practical in demeanor\n")
                .replace("- Likely to be helpful and engaging\n", "- Prioritizes useful information and concrete needs\n")
                .replace("- Can be friendly but has some concerns\n", "- Stays task-focused while explaining concerns\n")
                .replace("- Less interested in small talk, more focused on needs\n", "- Avoids small talk and focuses on immediate needs\n")
                .replace("- Do not use markdown, speak in plain text.", "- Do not use markdown, speak in plain text.\n- Prefer practical, concrete responses over dramatic language.");
    }
}
