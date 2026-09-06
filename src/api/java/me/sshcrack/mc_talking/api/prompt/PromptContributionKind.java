package me.sshcrack.mc_talking.api.prompt;

/**
 * Semantics of addon-contributed prompt content. Keeping observations, recollections and guidance
 * distinct lets core preserve the authority of current world facts over remembered claims.
 */
public enum PromptContributionKind {
    /** Current addon-observed state, such as an active errand or expedition status. */
    OBSERVATION,
    /** Remembered or historical context which may be stale. */
    RECOLLECTION,
    /** Behavioral guidance supplied by the addon, not a world-state fact. */
    INSTRUCTION
}
