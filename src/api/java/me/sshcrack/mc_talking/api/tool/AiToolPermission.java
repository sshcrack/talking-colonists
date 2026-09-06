package me.sshcrack.mc_talking.api.tool;

/**
 * Stable Talking Colonists permission vocabulary for addon AI tools.
 *
 * <p>Except for {@link #NONE}, values are mapped by core to the matching MineColonies server-side
 * permission. Addons should not depend on MineColonies permission enums directly; patch-level
 * upstream changes are isolated inside Talking Colonists.</p>
 */
public enum AiToolPermission {
    NONE,
    ACCESS_HUTS,
    PLACE_HUTS,
    BREAK_HUTS,
    EDIT_PERMISSIONS,
    MANAGE_HUTS,
    RECEIVE_MESSAGES,
    USE_SCAN_TOOL,
    PLACE_BLOCKS,
    BREAK_BLOCKS,
    TOSS_ITEM,
    PICKUP_ITEM,
    FILL_BUCKET,
    OPEN_CONTAINER,
    RIGHTCLICK_BLOCK,
    RIGHTCLICK_ENTITY,
    THROW_POTION,
    SHOOT_ARROW,
    ATTACK_CITIZEN,
    ATTACK_ENTITY,
    TELEPORT_TO_COLONY,
    EXPLODE,
    RALLY_GUARDS,
    HURT_CITIZEN,
    HURT_VISITOR,
    MAP_BORDER,
    MAP_DEATHS,
    ACCESS_TOGGLEABLES
}
