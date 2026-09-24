package me.sshcrack.mc_talking.api.colony;

/** Kind of a recorded colony event. New core kinds may be added in later 2.x versions. */
public enum ColonyEventType {
    RAID,
    CITIZEN_DEATH,
    CITIZEN_BORN,
    CITIZEN_HIRED,
    CITIZEN_RESURRECTED,
    CITIZEN_JOB_CHANGE,
    BUILDING_ADDED,
    BUILDING_REMOVED,
    BUILDING_UPGRADED,
    COLONY_FOUNDED,
    /** Recorded by an addon through {@link ColonyEventService#record}. */
    ADDON
}
