package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenSubState;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import org.jetbrains.annotations.Nullable;

/** Internal rendering of the stable typed activity model into human-readable prompt text. */
final class AIStateDescriber {
    private AIStateDescriber() {
    }

    @Nullable
    static String describe(
            CitizenActivityCategory category,
            @Nullable AIWorkerState workState,
            @Nullable CitizenSubState subState,
            @Nullable String nameTagDescription,
            @Nullable String workBuildingDisplayName
    ) {
        if (subState != null) {
            String detail = describeDetail(subState.type(), subState.context());
            if (detail != null) return detail;
        }

        return switch (category) {
            case EATING -> "Taking a break to eat or waiting at the restaurant for a meal.";
            case SLEEPING -> "Sleeping — resting for the night.";
            case SICK -> "Too sick to work. Needs medical attention at the hospital.";
            case MOURNING -> "Mourning the loss of a fellow colonist. Cannot focus on work right now.";
            case DANGER -> "Reacting to a nearby threat.";
            case WORKING -> describeWork(workState, nameTagDescription, workBuildingDisplayName);
            case IDLE -> nameTagDescription != null ? nameTagDescription + "." : "Idle.";
            case INACTIVE -> nameTagDescription != null ? nameTagDescription + "." : "Inactive.";
            case OTHER -> nameTagDescription != null ? nameTagDescription + "." : null;
        };
    }

    @Nullable
    private static String describeDetail(MinimalAISubState state, @Nullable String context) {
        return switch (state) {
            case EAT_CHECKING_FOOD -> "Checking pockets and inventory for food.";
            case EAT_GOING_TO_HUT -> "Walking to the workplace to grab some food.";
            case EAT_SEARCH_RESTAURANT, EAT_GOING_TO_RESTAURANT -> context != null
                    ? "Walking to " + context + " for a meal."
                    : "Walking to the restaurant for a meal.";
            case EAT_WAITING_FOOD -> context != null
                    ? "At " + context + ", waiting for food to be served."
                    : "Standing at the restaurant, waiting for the cook to prepare a meal.";
            case EAT_GETTING_FOOD_SELF -> "Too impatient to wait for the cook, grabbing food directly.";
            case EAT_GOING_TO_EAT_POS -> "Looking for a place to sit down and eat.";
            case EAT_EATING -> context != null ? "Eating a meal at " + context + "." : "Eating a meal.";

            case SLEEP_WALKING_TO_BED -> context != null
                    ? "Walking to " + context + " to sleep for the night."
                    : "Walking home to go to sleep for the night.";
            case SLEEP_FINDING_BED -> context != null
                    ? "Looking for a spare bed — " + context + "."
                    : "Looking for a free bed to sleep in.";
            case SLEEP_IN_BED -> "Fast asleep — dreaming the night away.";

            case SICK_CHECKING_FOR_CURE -> context != null
                    ? "Checking pockets desperately for medicine to treat " + context + "."
                    : "Checking their pockets desperately for medicine.";
            case SICK_WALKING_TO_HOSPITAL -> context != null
                    ? "Feeling ill with " + context + ", heading to the hospital."
                    : "Feeling very ill and making their way to the hospital.";
            case SICK_AT_HOSPITAL -> context != null
                    ? "Resting in bed at the hospital, being treated for " + context + "."
                    : "Resting in bed at the hospital, receiving treatment.";
            case SICK_RECEIVING_CURE -> context != null
                    ? "Receiving treatment for " + context + " — the medicine is starting to work."
                    : "Receiving medical treatment from the colony's healer.";
            case SICK_WANDERING -> context != null
                    ? "Too sick with " + context + " to function, wandering around aimlessly."
                    : "Too sick to function, wandering around aimlessly.";

            case MOURN_WALKING -> context != null
                    ? "Walking aimlessly while mourning " + context + "."
                    : "Walking aimlessly while mourning.";
            case MOURN_AT_TOWNHALL -> context != null
                    ? "Gathering at the town hall to mourn " + context + "."
                    : "Gathering at the town hall to mourn a fallen colonist.";
            case MOURN_WALKING_TO_GRAVEYARD -> context != null
                    ? "Walking to pay respects at " + context + "'s grave."
                    : "Walking to pay respects at a fallen colonist's grave.";
            case MOURN_AT_GRAVE -> context != null
                    ? "Standing quietly at " + context + "'s grave, grieving."
                    : "Standing quietly at a grave, grieving.";
            case MOURN_STARING -> context != null
                    ? "Staring into the distance, lost in grief over " + context + "."
                    : "Staring into the distance, lost in grief.";

            case FLEE_CHECKING -> "Looking around nervously for threats.";
            case FLEE_RUNNING -> "Running away from a threat!";

            case LEISURE_GOING_TO_SITE -> "Walking to a leisure site.";
            case LEISURE_WANDERING_AT_SITE -> "Relaxing at a leisure site.";
            case LEISURE_READING -> "Reading a book during free time.";
            case UNKNOWN -> null;
        };
    }

    private static String describeWork(
            @Nullable AIWorkerState workState,
            @Nullable String nameTagDescription,
            @Nullable String workBuildingDisplayName
    ) {
        if (workState == null || workState == AIWorkerState.UNKNOWN) {
            return nameTagDescription != null ? "Working — " + nameTagDescription + "." : "Working.";
        }
        return switch (workState) {
            case NEEDS_ITEM -> "Waiting at " + (workBuildingDisplayName != null ? workBuildingDisplayName : "the workplace")
                    + " for missing supplies to be delivered before work can continue.";
            case START_WORKING -> "Walking to " + (workBuildingDisplayName != null ? workBuildingDisplayName : "work") + ".";
            case IDLE, DECIDE -> "At work, deciding what to do next.";
            case PREPARE_DELIVERY -> "Collecting items from the warehouse for a delivery.";
            case DELIVERY -> "Currently delivering items to a colony building.";
            case PICKUP -> "Picking up surplus items from a building to bring back to the warehouse.";
            case DUMPING -> "Dropping off collected items at the warehouse.";
            case GUARD_PATROL -> "Patrolling the colony perimeter.";
            case GUARD_GUARD -> "Standing guard at an assigned post.";
            case GUARD_FOLLOW -> "Following and protecting a player.";
            case GUARD_REGEN -> "Resting at the guard tower to recover health.";
            case HELP_CITIZEN -> "Rushing to help a citizen who is in danger.";
            case FARMER_HOE -> "Hoeing the fields.";
            case FARMER_PLANT -> "Planting seeds.";
            case FARMER_HARVEST -> "Harvesting crops.";
            case MINER_MINING_NODE, MINER_MINING_SHAFT -> "Mining underground.";
            case BUILDING_STEP, START_BUILDING -> "Building or repairing a structure.";
            case COOK_SERVE_FOOD_TO_CITIZEN -> "Preparing and serving food to colonists.";
            default -> nameTagDescription != null ? "Working — " + nameTagDescription + "." : "Working.";
        };
    }
}
