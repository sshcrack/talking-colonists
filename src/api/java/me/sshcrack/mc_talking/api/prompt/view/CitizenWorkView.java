package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Work, housing, skills and request/quest information. */
public record CitizenWorkView(
        @Nullable String jobName,
        @Nullable BuildingView home,
        @Nullable BuildingView workplace,
        @NotNull List<SkillLevelView> skills,
        @NotNull List<String> fulfillableItemRequests,
        @NotNull List<String> blockedItemRequests,
        @NotNull List<String> activeQuests
) {
    public CitizenWorkView {
        skills = List.copyOf(skills);
        fulfillableItemRequests = List.copyOf(fulfillableItemRequests);
        blockedItemRequests = List.copyOf(blockedItemRequests);
        activeQuests = List.copyOf(activeQuests);
    }
}
