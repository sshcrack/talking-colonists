package me.sshcrack.mc_talking.api.tool;

/**
 * Determines which Talking Colonists sessions may advertise and execute an addon tool.
 */
public enum AiToolScope {
    /** The tool may be called from player and system-controlled citizen sessions. */
    ANY_SESSION,
    /** The tool is only available while an authenticated player is directly speaking to the citizen. */
    PLAYER_CONVERSATION
}
