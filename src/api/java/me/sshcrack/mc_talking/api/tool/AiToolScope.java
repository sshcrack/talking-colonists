package me.sshcrack.mc_talking.api.tool;

/**
 * Determines which Talking Colonists sessions may advertise and execute an addon tool.
 */
public enum AiToolScope {
    /** The tool may be called from player and system-controlled citizen sessions. */
    ANY_SESSION,
    /**
     * The tool requires authoritative player identity. This includes direct player conversations and
     * controlled addon turns explicitly bound by {@code ControlledConversationSession.addPlayerStatement}.
     */
    PLAYER_CONVERSATION
}
