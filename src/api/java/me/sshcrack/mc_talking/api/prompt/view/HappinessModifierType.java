package me.sshcrack.mc_talking.api.prompt.view;

/**
 * Stable Talking Colonists compatibility view of MineColonies happiness modifier identifiers.
 * Unknown/new upstream modifiers map to {@link #UNKNOWN} until Talking Colonists adds a semantic value.
 */
@SuppressWarnings("SpellCheckingInspection")
public enum HappinessModifierType {
    HOMELESSNESS,
    UNEMPLOYMENT,
    HEALTH,
    IDLE_AT_JOB,
    SCHOOL,
    MYSTICAL_SITE,
    SECURITY,
    SOCIAL,
    DAMAGE,
    DEATH,
    RAID_WITHOUT_DEATH,
    SLEPT_TONIGHT,
    QUEST,
    FOOD,
    GREAT_FOOD,
    UNKNOWN
}
