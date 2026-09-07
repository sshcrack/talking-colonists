package me.sshcrack.mc_talking.api.memory;

/** Where a persistent recollection came from. */
public enum MemoryProvenance {
    /** A concrete gameplay event observed by Talking Colonists. */
    OBSERVED_EVENT,
    /** A statement explicitly attributed to a player UUID. */
    PLAYER_STATEMENT,
    /** Speech/thought attributed to a citizen UUID; not proof of player action or promises. */
    CITIZEN_STATEMENT,
    /** Low-level memory write made directly through the addon API without stronger attribution. */
    ADDON_DIRECT_WRITE,
    /** Outcome explicitly confirmed by an addon through the idempotent confirmation API. */
    ADDON_CONFIRMED_OUTCOME,
    /** Data loaded from saves created before provenance existed. */
    LEGACY_UNATTRIBUTED
}
