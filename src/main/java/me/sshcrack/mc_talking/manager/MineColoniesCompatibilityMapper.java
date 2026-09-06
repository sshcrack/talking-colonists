package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenAIState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenSkill;
import me.sshcrack.mc_talking.api.prompt.view.CitizenStatusType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Single compatibility boundary between MineColonies runtime values and the stable addon API.
 *
 * <p>Do not spread MineColonies enum/name translation across prompt providers, tools, or addon
 * services. Patch-level MineColonies changes should require updating this class and its tests, not
 * addon code.</p>
 */
final class MineColoniesCompatibilityMapper {
    private MineColoniesCompatibilityMapper() {
    }

    static @NotNull CitizenAIState citizenState(
            @NotNull com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState state
    ) {
        try {
            return CitizenAIState.valueOf(state.name());
        } catch (IllegalArgumentException ignored) {
            return CitizenAIState.UNKNOWN;
        }
    }

    static @NotNull AIWorkerState workerState(
            @NotNull com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState state
    ) {
        try {
            return AIWorkerState.valueOf(state.name());
        } catch (IllegalArgumentException ignored) {
            return AIWorkerState.UNKNOWN;
        }
    }

    static @NotNull CitizenSkill skill(@NotNull Skill skill) {
        try {
            return CitizenSkill.valueOf(skill.name().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return CitizenSkill.UNKNOWN;
        }
    }

    static @NotNull CitizenStatusType status(@NotNull VisibleCitizenStatus status) {
        return visibleStatusTranslationKey(status.getTranslationKey());
    }

    static @NotNull CitizenStatusType visibleStatusTranslationKey(@NotNull String translationKey) {
        return switch (translationKey) {
            case "com.minecolonies.gui.visiblestatus.working" -> CitizenStatusType.WORKING;
            case "com.minecolonies.gui.visiblestatus.sleep" -> CitizenStatusType.SLEEP;
            case "com.minecolonies.gui.visiblestatus.idle" -> CitizenStatusType.HOUSE;
            case "com.minecolonies.gui.visiblestatus.raid" -> CitizenStatusType.RAIDED;
            case "com.minecolonies.gui.visiblestatus.mourn" -> CitizenStatusType.MOURNING;
            case "com.minecolonies.gui.visiblestatus.rain" -> CitizenStatusType.BAD_WEATHER;
            case "com.minecolonies.gui.visiblestatus.sick" -> CitizenStatusType.SICK;
            case "com.minecolonies.gui.visiblestatus.eat" -> CitizenStatusType.EAT;
            default -> CitizenStatusType.UNKNOWN;
        };
    }

    static @NotNull HappinessModifierType happinessModifier(@NotNull String id) {
        return switch (id) {
            case "homelessness" -> HappinessModifierType.HOMELESSNESS;
            case "unemployment" -> HappinessModifierType.UNEMPLOYMENT;
            case "health" -> HappinessModifierType.HEALTH;
            case "idleatjob" -> HappinessModifierType.IDLE_AT_JOB;
            case "school" -> HappinessModifierType.SCHOOL;
            case "mysticalsite" -> HappinessModifierType.MYSTICAL_SITE;
            case "security" -> HappinessModifierType.SECURITY;
            case "social" -> HappinessModifierType.SOCIAL;
            case "damage" -> HappinessModifierType.DAMAGE;
            case "death" -> HappinessModifierType.DEATH;
            case "raidwithoutdeath" -> HappinessModifierType.RAID_WITHOUT_DEATH;
            case "slepttonight" -> HappinessModifierType.SLEPT_TONIGHT;
            case "quest" -> HappinessModifierType.QUEST;
            case "food" -> HappinessModifierType.FOOD;
            case "greatfood" -> HappinessModifierType.GREAT_FOOD;
            default -> HappinessModifierType.UNKNOWN;
        };
    }

    static @NotNull MinimalAISubState eatingState(
            @NotNull com.minecolonies.core.entity.ai.minimal.EntityAIEatTask.EatingState state
    ) {
        return switch (state.name()) {
            case "CHECK_FOR_FOOD" -> MinimalAISubState.EAT_CHECKING_FOOD;
            case "GO_TO_HUT" -> MinimalAISubState.EAT_GOING_TO_HUT;
            case "SEARCH_RESTAURANT" -> MinimalAISubState.EAT_SEARCH_RESTAURANT;
            case "GO_TO_RESTAURANT" -> MinimalAISubState.EAT_GOING_TO_RESTAURANT;
            case "WAIT_FOR_FOOD" -> MinimalAISubState.EAT_WAITING_FOOD;
            case "GET_FOOD_YOURSELF" -> MinimalAISubState.EAT_GETTING_FOOD_SELF;
            case "GO_TO_EAT_POS" -> MinimalAISubState.EAT_GOING_TO_EAT_POS;
            case "EAT", "DONE" -> MinimalAISubState.EAT_EATING;
            default -> MinimalAISubState.UNKNOWN;
        };
    }

    static @NotNull MinimalAISubState sleepState(
            @NotNull com.minecolonies.core.entity.ai.minimal.EntityAISleep.SleepState state
    ) {
        return switch (state.name()) {
            case "WALKING_HOME" -> MinimalAISubState.SLEEP_WALKING_TO_BED;
            case "FIND_BED" -> MinimalAISubState.SLEEP_FINDING_BED;
            case "SLEEPING" -> MinimalAISubState.SLEEP_IN_BED;
            default -> MinimalAISubState.UNKNOWN;
        };
    }

    static @NotNull MinimalAISubState diseaseState(
            @NotNull com.minecolonies.core.entity.ai.minimal.EntityAISickTask.DiseaseState state
    ) {
        return switch (state.name()) {
            case "CHECK_FOR_CURE" -> MinimalAISubState.SICK_CHECKING_FOR_CURE;
            case "GO_TO_HUT", "SEARCH_HOSPITAL", "GO_TO_HOSPITAL" -> MinimalAISubState.SICK_WALKING_TO_HOSPITAL;
            case "WAIT_FOR_CURE", "FIND_EMPTY_BED" -> MinimalAISubState.SICK_AT_HOSPITAL;
            case "APPLY_CURE" -> MinimalAISubState.SICK_RECEIVING_CURE;
            case "WANDER" -> MinimalAISubState.SICK_WANDERING;
            default -> MinimalAISubState.UNKNOWN;
        };
    }

    static @NotNull MinimalAISubState mourningState(
            @NotNull com.minecolonies.core.entity.ai.minimal.EntityAIMournCitizen.MourningState state
    ) {
        return switch (state.name()) {
            case "DECIDE", "WANDERING" -> MinimalAISubState.MOURN_WALKING;
            case "WALKING_TO_TOWNHALL" -> MinimalAISubState.MOURN_AT_TOWNHALL;
            case "WALKING_TO_GRAVEYARD" -> MinimalAISubState.MOURN_WALKING_TO_GRAVEYARD;
            case "WANDER_AT_GRAVEYARD", "WALK_TO_GRAVE" -> MinimalAISubState.MOURN_AT_GRAVE;
            case "STARING" -> MinimalAISubState.MOURN_STARING;
            default -> MinimalAISubState.UNKNOWN;
        };
    }

    static @NotNull MinimalAISubState fleeState(
            @NotNull com.minecolonies.core.entity.ai.minimal.EntityAICitizenAvoidEntity.FleeStates state
    ) {
        return switch (state.name()) {
            case "CHECK_ENTITIES" -> MinimalAISubState.FLEE_CHECKING;
            case "RUNNING" -> MinimalAISubState.FLEE_RUNNING;
            default -> MinimalAISubState.UNKNOWN;
        };
    }

    static @NotNull MinimalAISubState wanderState(
            @NotNull com.minecolonies.core.entity.ai.minimal.EntityAICitizenWander.WanderState state
    ) {
        return switch (state.name()) {
            case "GO_TO_LEISURE_SITE" -> MinimalAISubState.LEISURE_GOING_TO_SITE;
            case "WANDER_AT_LEISURE_SITE" -> MinimalAISubState.LEISURE_WANDERING_AT_SITE;
            case "READ_A_BOOK" -> MinimalAISubState.LEISURE_READING;
            default -> MinimalAISubState.UNKNOWN;
        };
    }
}
