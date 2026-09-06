package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Current physical/emotional needs and blockers. */
public record CitizenWellbeingView(
        boolean sick,
        boolean homeless,
        double saturation,
        @Nullable Double healthPercent,
        double happiness,
        @NotNull List<HappinessModifierView> happinessModifiers,
        boolean hasSchool,
        @NotNull List<String> blockingInteractionMessages,
        @Nullable ColonyFoodSituation foodSituation
) {
    public CitizenWellbeingView {
        happinessModifiers = List.copyOf(happinessModifiers);
        blockingInteractionMessages = List.copyOf(blockingInteractionMessages);
    }
}
