package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

/** Stable semantic happiness modifier and its current factor. */
public record HappinessModifierView(@NotNull HappinessModifierType type, double factor) {
}
