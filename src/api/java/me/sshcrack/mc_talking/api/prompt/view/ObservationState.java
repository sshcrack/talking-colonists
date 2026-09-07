package me.sshcrack.mc_talking.api.prompt.view;

/** Freshness/availability of a verified prompt observation. */
public enum ObservationState {
    /** Read from the authoritative source while this snapshot was assembled. */
    CURRENT,
    /** A previously observed value is retained but is not current anymore. */
    STALE,
    /** The authoritative entity/chunk required for this value is not loaded. */
    UNLOADED,
    /** The backing API did not expose usable data for this value. */
    UNAVAILABLE
}
