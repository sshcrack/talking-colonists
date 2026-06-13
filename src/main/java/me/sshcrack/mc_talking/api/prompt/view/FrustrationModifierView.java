package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.Nullable;

public record FrustrationModifierView(
    HappinessModifierType type,
    double factor,
    long rawDurationTicks,
    long adjustedDurationTicks,
    FrustrationTier tier,
    @Nullable String contextNote
) {}
