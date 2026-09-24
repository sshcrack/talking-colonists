package me.sshcrack.mc_talking.api.memory;

/** How a published broadcast reaches citizens. */
public enum BroadcastScope {
    /** Every citizen of the colony learns the broadcast immediately. */
    COLONY_IMMEDIATE,
    /**
     * One citizen (or the citizens around a position) learns it first, then it spreads through the
     * normal citizen-to-citizen broadcast propagation.
     */
    PROPAGATE_FROM
}
