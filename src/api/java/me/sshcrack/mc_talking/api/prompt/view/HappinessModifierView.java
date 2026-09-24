package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

/**
 * Stable semantic happiness modifier and its current factor.
 *
 * @param activeDays colony days this modifier has been negative without a break, as counted by
 *                   MineColonies for its time-based modifiers (homelessness, unemployment, health,
 *                   idle at job); {@code 0} for other modifiers or when unknown
 */
public record HappinessModifierView(@NotNull HappinessModifierType type, double factor, int activeDays) {
    public HappinessModifierView {
        activeDays = Math.max(0, activeDays);
    }

    /** Modifier without a known duration. */
    public HappinessModifierView(@NotNull HappinessModifierType type, double factor) {
        this(type, factor, 0);
    }
}
