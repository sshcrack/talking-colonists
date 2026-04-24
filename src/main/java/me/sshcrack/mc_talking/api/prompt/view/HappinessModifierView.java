package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable happiness modifier data for prompt generation.
 */
public record HappinessModifierView(
        HappinessModifierType type,
        double factor
) {
}
