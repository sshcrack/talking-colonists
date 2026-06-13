package me.sshcrack.mc_talking.api.prompt.view;

import java.util.List;

public record FrustrationData(
    FrustrationTier overallTier,
    List<FrustrationModifierView> modifiers,
    boolean isInCooldown
) {
    public static final FrustrationData EMPTY =
        new FrustrationData(FrustrationTier.NEUTRAL, List.of(), false);

    public static final FrustrationData COOLDOWN =
        new FrustrationData(FrustrationTier.NEUTRAL, List.of(), true);
}
