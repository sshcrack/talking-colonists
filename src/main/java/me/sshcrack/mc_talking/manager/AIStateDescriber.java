package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenAIState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import org.jetbrains.annotations.Nullable;

/**
 * Pure formatting utilities shared between {@link DefaultCitizenPromptProvider}
 * and {@link me.sshcrack.mc_talking.manager.tools.GetCurrentSituationAction}.
 */
public final class AIStateDescriber {

    private AIStateDescriber() {
    }

    /**
     * Produces a human-readable sentence describing what the citizen is currently doing,
     * based on their AI state, work AI sub-state, name-tag description, and minimal AI sub-state.
     *
     * @return a sentence, or {@code null} if no state is loaded
     */
    @Nullable
    public static String describeAiState(
            CitizenAIState citizenAiState, AIWorkerState workAiState,
            String nameTagDescription, CitizenPromptView view) {

        if (citizenAiState == null && workAiState == null) return null;

        var cs = view.subState();
        MinimalAISubState sub = cs != null ? cs.type() : null;
        String ctx = cs != null ? cs.context() : null;
        if (sub != null && isSubStateConsistentWithAiState(sub, citizenAiState)) {
            return switch (sub) {
                case EAT_CHECKING_FOOD -> "Checking pockets and inventory for food.";
                case EAT_GOING_TO_HUT -> "Walking to the workplace to grab some food.";
                case EAT_SEARCH_RESTAURANT -> ctx != null
                        ? "Walking to " + ctx + " for a meal."
                        : "Hungry and walking to the restaurant, hoping to find a free table.";
                case EAT_GOING_TO_RESTAURANT -> ctx != null
                        ? "Walking to " + ctx + " for a meal."
                        : "Walking to the restaurant for a meal.";
                case EAT_WAITING_FOOD -> ctx != null
                        ? "At " + ctx + ", waiting for food to be served."
                        : "Standing at the restaurant, waiting for the cook to prepare a meal.";
                case EAT_GETTING_FOOD_SELF -> "Too impatient to wait for the cook, grabbing food directly.";
                case EAT_GOING_TO_EAT_POS -> "Looking for a place to sit down and eat.";
                case EAT_EATING -> ctx != null
                        ? "Eating a meal at " + ctx + "."
                        : "Sitting down and eating a meal at the restaurant.";
                case EAT_GET_FOOD -> ctx != null
                        ? "At " + ctx + ", waiting for food to be served."
                        : "At the restaurant, waiting for food to be served.";
                case SLEEP_WALKING_TO_BED -> ctx != null
                        ? "Walking to " + ctx + " to sleep for the night."
                        : "Walking home to go to sleep for the night.";
                case SLEEP_FINDING_BED -> ctx != null
                        ? "Looking for a spare bed — " + ctx + "."
                        : "Looking for a free bed to sleep in.";
                case SLEEP_IN_BED -> "Fast asleep — dreaming the night away.";
                case MOURN_WALKING -> ctx != null
                        ? "Walking aimlessly while mourning " + ctx + "."
                        : "Walking aimlessly while mourning.";
                case MOURN_AT_TOWNHALL -> ctx != null
                        ? "Gathering at the town hall to mourn " + ctx + "."
                        : "Gathering at the town hall to mourn a fallen colonist.";
                case MOURN_WALKING_TO_GRAVEYARD -> ctx != null
                        ? "Walking to pay respects at " + ctx + "'s grave."
                        : "Walking to pay respects at a fallen colonist's grave.";
                case MOURN_AT_GRAVE -> ctx != null
                        ? "Standing quietly at " + ctx + "'s grave, grieving."
                        : "Standing quietly at a grave, grieving.";
                case MOURN_STARING -> ctx != null
                        ? "Staring into the distance, lost in grief over " + ctx + "."
                        : "Staring into the distance, lost in grief.";
                case SICK_CHECKING_FOR_CURE -> ctx != null
                        ? "Checking pockets desperately for medicine to treat " + ctx + "."
                        : "Checking their pockets desperately for medicine.";
                case SICK_WALKING_TO_HOSPITAL -> ctx != null
                        ? "Feeling ill with " + ctx + ", heading to the hospital."
                        : "Feeling very ill and making their way to the hospital.";
                case SICK_AT_HOSPITAL -> ctx != null
                        ? "Resting in bed at the hospital, being treated for " + ctx + "."
                        : "Resting in bed at the hospital, receiving treatment.";
                case SICK_RECEIVING_CURE -> ctx != null
                        ? "Receiving treatment for " + ctx + " — the medicine is starting to work."
                        : "Receiving medical treatment from the colony's healer.";
                case SICK_WANDERING -> ctx != null
                        ? "Too sick with " + ctx + " to function, wandering around aimlessly."
                        : "Too sick to function, wandering around aimlessly.";
                case FLEE_CHECKING -> "Looking around nervously for threats.";
                case FLEE_RUNNING -> "Running away from a threat!";
            };
        }

        if (citizenAiState == CitizenAIState.EATING) {
            return "Taking a break to eat or waiting at the restaurant for a meal.";
        }

        if (citizenAiState == CitizenAIState.SLEEP) {
            return "Sleeping — resting for the night.";
        }

        if (citizenAiState == CitizenAIState.SICK) {
            return "Too sick to work. Needs medical attention at the hospital.";
        }

        if (citizenAiState == CitizenAIState.MOURN) {
            return "Mourning the loss of a fellow colonist. Cannot focus on work right now.";
        }

        if (citizenAiState == CitizenAIState.WORKING || citizenAiState == CitizenAIState.WORK) {
            if (workAiState == null) {
                return nameTagDescription != null
                        ? "Working — " + nameTagDescription + "."
                        : "Working.";
            }
            return switch (workAiState) {
                case NEEDS_ITEM
                    -> "Waiting at " + (view.workBuildingDisplayName() != null
                        ? view.workBuildingDisplayName() : "the workplace")
                        + " for missing supplies to be delivered before work can continue.";
                case START_WORKING
                    -> "Walking to " + (view.workBuildingDisplayName() != null
                        ? view.workBuildingDisplayName() : "work") + ".";
                case IDLE, DECIDE
                    -> "At work, deciding what to do next.";
                case PREPARE_DELIVERY
                    -> "Collecting items from the warehouse for a delivery.";
                case DELIVERY
                    -> "Currently delivering items to a colony building.";
                case PICKUP
                    -> "Picking up surplus items from a building to bring back to the warehouse.";
                case DUMPING
                    -> "Dropping off collected items at the warehouse.";
                case GUARD_PATROL  -> "Patrolling the colony perimeter.";
                case GUARD_GUARD   -> "Standing guard at an assigned post.";
                case GUARD_FOLLOW  -> "Following and protecting a player.";
                case GUARD_REGEN   -> "Resting at the guard tower to recover health.";
                case HELP_CITIZEN  -> "Rushing to help a citizen who is in danger.";
                case FARMER_HOE     -> "Hoeing the fields.";
                case FARMER_PLANT   -> "Planting seeds.";
                case FARMER_HARVEST -> "Harvesting crops.";
                case MINER_MINING_NODE, MINER_MINING_SHAFT -> "Mining underground.";
                case BUILDING_STEP, START_BUILDING -> "Building or repairing a structure.";
                case COOK_SERVE_FOOD_TO_CITIZEN -> "Preparing and serving food to colonists.";
                default -> nameTagDescription != null
                        ? "Working — " + nameTagDescription + "."
                        : "Working.";
            };
        }

        return nameTagDescription != null ? nameTagDescription + "." : null;
    }

    /**
     * Returns true when the given minimal-AI sub-state is consistent with the top-level
     * citizen AI state (i.e. both describe the same activity).
     */
    public static boolean isSubStateConsistentWithAiState(MinimalAISubState sub, @Nullable CitizenAIState aiState) {
        if (aiState == null) return false;
        return switch (sub) {
            case EAT_CHECKING_FOOD, EAT_GOING_TO_HUT, EAT_SEARCH_RESTAURANT, EAT_GOING_TO_RESTAURANT, EAT_WAITING_FOOD, EAT_GETTING_FOOD_SELF, EAT_GOING_TO_EAT_POS, EAT_EATING, EAT_GET_FOOD -> aiState == CitizenAIState.EATING;
            case SLEEP_WALKING_TO_BED, SLEEP_FINDING_BED, SLEEP_IN_BED -> aiState == CitizenAIState.SLEEP;
            case MOURN_WALKING, MOURN_AT_TOWNHALL, MOURN_WALKING_TO_GRAVEYARD, MOURN_AT_GRAVE, MOURN_STARING -> aiState == CitizenAIState.MOURN;
            case SICK_CHECKING_FOR_CURE, SICK_WALKING_TO_HOSPITAL, SICK_AT_HOSPITAL, SICK_RECEIVING_CURE, SICK_WANDERING -> aiState == CitizenAIState.SICK;
            case FLEE_CHECKING, FLEE_RUNNING -> aiState == CitizenAIState.FLEE;
        };
    }
}
