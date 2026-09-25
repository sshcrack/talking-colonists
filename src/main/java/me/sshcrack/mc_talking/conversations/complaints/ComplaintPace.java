package me.sshcrack.mc_talking.conversations.complaints;

import org.jetbrains.annotations.Nullable;

/** How quickly a personality loses patience with a problem that is not fixed. */
public enum ComplaintPace {
    /** Stays patient longer. */
    PATIENT(-1),
    NORMAL(0),
    /** Gets frustrated sooner. */
    QUICK(1),
    /** Never gets angry: goes quiet and gives up instead. */
    WITHDRAWN(0);

    final int shift;

    ComplaintPace(int shift) {
        this.shift = shift;
    }

    /** The pace of a personality archetype by name (e.g. "GRUMP"); unknown or none is normal. */
    public static ComplaintPace of(@Nullable String archetype) {
        if (archetype == null) return NORMAL;
        return switch (archetype) {
            case "GRUMP", "SARCASTIC", "COMPETITIVE", "DRAMATIC", "BOASTFUL" -> QUICK;
            case "OPTIMIST", "NURTURING", "STOIC", "PHILOSOPHICAL" -> PATIENT;
            case "TIMID", "ANXIOUS" -> WITHDRAWN;
            default -> NORMAL;
        };
    }
}
