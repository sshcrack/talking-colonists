package me.sshcrack.mc_talking.api.prompt.view;

import me.sshcrack.mc_talking.config.PersonalityArchetype;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stable, MineColonies-agnostic data view used for prompt generation.
 *
 * @param interestedParties     Parties that may be included in the prompt for the memory of this citizen
 * @param colonyId              MineColonies colony ID — used for raid-trauma and personality lookups
 * @param peaceful              {@code true} when the colony's world is on peaceful difficulty — suppresses raid-related prompt content
 * @param personality           Built-in personality archetype, or {@code null} if a custom one is active
 * @param customPersonalityText Freeform custom personality text, or {@code null} if a built-in is active
 * @param playerState           Description of the speaking player's health and armor, e.g. "healthy (20/20 HP) wearing iron armor
 * @param environment           Description of the current environment, e.g. "It is midday and sunny."
 * @param activeItemRequests    Human-readable descriptions of resources this citizen is waiting for, or {@code null} if none
 * @param guard                 {@code true} when the citizen is a guard (knight/archer)
 * @param activeQuests Human-readable descriptions of active quests this citizen is involved in, or {@code null} if none
 * @param recentColonyEvents Descriptions of recent colony lifecycle events (deaths, births, building changes), or empty list if none
 * @param colonyConnections     Diplomatic connections to neighboring colonies, e.g. "Oakvale (ALLY)", or {@code null} if none
 * @param colonyMilestone Colony milestone description (buildings built, mobs killed, etc.), or {@code null} if none
 * @param citizenAiState        Current CitizenAI state, or {@code null} if entity not loaded
 * @param workAiState           Current work AI state, or {@code null} if entity not loaded
 * @param nameTagDescription    Job nametag description, the citizen's current activity text — from {@code /mc citizens info}, or {@code null} if unavailable
 * @param minimalAiSubState     Fine-grained sub-state from the entity's minimal AI (eating/sleep/mourn/sick/flee phases), or {@code null} if entity not loaded or no sub-state is active
 */
public record CitizenPromptView(
        String name,
        boolean child,
        boolean female,
        @Nullable String jobName,
        boolean guard,
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
        int colonyId,
        boolean peaceful,
        @Nullable PersonalityArchetype personality,
        @Nullable String customPersonalityText,
        @Nullable String playerState,
        @Nullable String environment,
        @Nullable List<String> activeItemRequests,
        @Nullable List<String> activeQuests,
        List<String> recentColonyEvents,
        @Nullable List<String> colonyConnections,
        @Nullable String colonyMilestone,
        @Nullable CitizenAIState citizenAiState,
        @Nullable AIWorkerState workAiState,
        @Nullable String nameTagDescription,
        @Nullable String colonyFoodSituation,
        @Nullable List<String> recentActions,
        @Nullable MinimalAISubState minimalAiSubState
) {
}
