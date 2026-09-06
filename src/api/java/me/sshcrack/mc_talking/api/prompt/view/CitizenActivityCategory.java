package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable semantic activity categories owned by Talking Colonists.
 *
 * <p>These are deliberately broader than MineColonies AI state enums. Exact upstream state names
 * remain available separately as raw IDs on {@link CitizenActivityView}.</p>
 */
public enum CitizenActivityCategory {
    IDLE,
    DANGER,
    EATING,
    SICK,
    SLEEPING,
    MOURNING,
    WORKING,
    INACTIVE,
    OTHER
}
