package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable Talking Colonists compatibility view of {@code com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState}
 * MineColonies citizen AI states. Addons depend on this enum rather than the upstream enum so MineColonies patch-level API changes are absorbed by Talking Colonists.
 */
public enum CitizenAIState {
    IDLE(),
    FLEE(),
    EATING(),
    SICK(),
    SLEEP,
    MOURN,
    WORK,
    WORKING,
    INACTIVE(),
    /** MineColonies exposed a state this API generation does not know yet. */
    UNKNOWN();

    CitizenAIState() {
    }
}
