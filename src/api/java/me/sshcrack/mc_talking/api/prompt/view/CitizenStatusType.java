package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable Talking Colonists compatibility view of MineColonies visible citizen statuses.
 *
 * <p>{@link #UNKNOWN} absorbs statuses introduced by a MineColonies patch until Talking Colonists
 * is updated, keeping addon binaries source-compatible with the stable API.</p>
 */
public enum CitizenStatusType {
    WORKING,
    SLEEP,
    HOUSE,
    RAIDED,
    MOURNING,
    BAD_WEATHER,
    SICK,
    EAT,
    UNKNOWN
}
