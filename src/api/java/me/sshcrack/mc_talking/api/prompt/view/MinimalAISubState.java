package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable Talking Colonists view of fine-grained minimal citizen AI phases.
 *
 * <p>This intentionally normalizes several MineColonies core-internal state enums into one typed
 * addon-facing enum, so addon authors get autocomplete/exhaustive switches without depending on
 * MineColonies implementation packages.</p>
 */
public enum MinimalAISubState {
    EAT_CHECKING_FOOD,
    EAT_GOING_TO_HUT,
    EAT_SEARCH_RESTAURANT,
    EAT_GOING_TO_RESTAURANT,
    EAT_WAITING_FOOD,
    EAT_GETTING_FOOD_SELF,
    EAT_GOING_TO_EAT_POS,
    EAT_EATING,

    SLEEP_WALKING_TO_BED,
    SLEEP_FINDING_BED,
    SLEEP_IN_BED,

    MOURN_WALKING,
    MOURN_AT_TOWNHALL,
    MOURN_WALKING_TO_GRAVEYARD,
    MOURN_AT_GRAVE,
    MOURN_STARING,

    SICK_CHECKING_FOR_CURE,
    SICK_WALKING_TO_HOSPITAL,
    SICK_AT_HOSPITAL,
    SICK_RECEIVING_CURE,
    SICK_WANDERING,

    FLEE_CHECKING,
    FLEE_RUNNING,

    LEISURE_GOING_TO_SITE,
    LEISURE_WANDERING_AT_SITE,
    LEISURE_READING,

    /** MineColonies exposed a fine-grained state this API generation does not know yet. */
    UNKNOWN
}
