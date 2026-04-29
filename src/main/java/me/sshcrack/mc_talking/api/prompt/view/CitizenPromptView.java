package me.sshcrack.mc_talking.api.prompt.view;

import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stable, MineColonies-agnostic data view used for prompt generation.
 * @param interestedParties Parties that may be included in the prompt for the memory of this citizen
 */
public record CitizenPromptView(
        String name,
        boolean child,
        boolean female,
        @Nullable String jobName,
        boolean sick,
        boolean homeless,
        List<String> parentNames,
        boolean hasPartner,
        List<String> childNames,
        List<String> siblingNames,
        double saturation,
        @Nullable Double healthPercent,
        @Nullable CitizenStatusView status,
        double happiness,
        List<HappinessModifierView> happinessModifiers,
        boolean hasSchool,
        List<SkillLevelView> skills,
        List<String> blockingInteractionMessages,
        @Nullable PlayerRelationView playerRelation,
        String responseLanguageName,
        @Nullable CitizenMemories memories,
        Map<UUID, String> interestedParties
) {
}
