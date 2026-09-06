package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Mirror of {@code com.minecolonies.api.entity.ai.statemachine.states.CitizenAIState}
 * from MineColonies. Interface {@code IState} stripped — used here purely as a value type.
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
    INACTIVE();

    CitizenAIState() {
    }
}
