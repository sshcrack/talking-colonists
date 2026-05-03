package me.sshcrack.mc_talking.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks when raid waves end per colony so that citizen prompts can include
 * post-raid trauma context for a configurable time window.
 *
 * <p>Call {@link #recordRaid(int, int)} from the {@code RaidManagerMixin}
 * whenever {@code RaidManager.onRaidEventFinished} concludes the last raid of
 * a wave. Query with {@link #isInTrauma(int, int)} when building prompts.</p>
 */
public final class RaidTraumaTracker {

    private RaidTraumaTracker() {}

    /** colonyId → wall-clock millis at which the last raid ended */
    private static final Map<Integer, Long> lastRaidEndTime = new ConcurrentHashMap<>();

    /** colonyId → number of citizens lost in the last raid */
    private static final Map<Integer, Integer> lastRaidLostCitizens = new ConcurrentHashMap<>();

    /**
     * Records that a raid just ended for the given colony.
     *
     * @param colonyId     the MineColonies colony ID
     * @param lostCitizens citizens who died during the raid
     */
    public static void recordRaid(int colonyId, int lostCitizens) {
        lastRaidEndTime.put(colonyId, System.currentTimeMillis());
        lastRaidLostCitizens.put(colonyId, lostCitizens);
    }

    /**
     * Returns {@code true} if the colony is still within the trauma window.
     *
     * @param colonyId        the colony to check
     * @param durationSeconds how long the trauma window lasts (from config)
     */
    public static boolean isInTrauma(int colonyId, int durationSeconds) {
        if (durationSeconds <= 0) return false;
        Long lastEnd = lastRaidEndTime.get(colonyId);
        if (lastEnd == null) return false;
        return (System.currentTimeMillis() - lastEnd) < (durationSeconds * 1000L);
    }

    /**
     * How many milliseconds have elapsed since the last raid ended, or
     * {@link Long#MAX_VALUE} if no raid has been recorded.
     */
    public static long millisSinceRaid(int colonyId) {
        Long lastEnd = lastRaidEndTime.get(colonyId);
        if (lastEnd == null) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastEnd;
    }

    /**
     * Citizens lost during the last raid for a colony, or {@code 0} if unknown.
     */
    public static int getLostCitizens(int colonyId) {
        return lastRaidLostCitizens.getOrDefault(colonyId, 0);
    }
}
