package me.sshcrack.mc_talking.api.prompt.view;

import me.sshcrack.mc_talking.config.PersonalityArchetype;
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
        Map<UUID, String> interestedParties,
        String colonyName,
        @Nullable String homeBuildingDisplayName,
        int homeBuildingLevel,
        @Nullable String workBuildingDisplayName,
        int workBuildingLevel,
        /** MineColonies colony ID — used for raid-trauma and personality lookups */
        int colonyId,
        /** Built-in personality archetype, or {@code null} if a custom one is active */
        @Nullable PersonalityArchetype personality,
        /** Freeform custom personality text, or {@code null} if a built-in is active */
        @Nullable String customPersonalityText,
        /** Description of the speaking player's health and armor, e.g. "healthy (20/20 HP) wearing iron armor" */
        @Nullable String playerState,
        /** Description of the current environment, e.g. "It is midday and sunny." */
        @Nullable String environment,
        /** Human-readable descriptions of resources this citizen is waiting for, or {@code null} if none */
        @Nullable List<String> activeItemRequests
) {
}

