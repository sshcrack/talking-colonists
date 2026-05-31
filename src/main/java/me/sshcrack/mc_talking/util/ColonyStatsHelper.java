package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.constant.Constants;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.util.concurrent.ThreadLocalRandom;

public final class ColonyStatsHelper {

    private ColonyStatsHelper() {
    }

    private static final int THRESHOLD_BUILDS = 50;
    private static final int THRESHOLD_KILLED = 100;
    private static final int THRESHOLD_DEATHS = 20;
    private static final int THRESHOLD_RAIDS = 10;
    private static final int THRESHOLD_CITIZENS = 10;

    /**
     * Returns a 1-sentence milestone mention for the citizen's colony, or null
     * if no milestone is currently notable.
     */
    public static String getColonyMilestoneText(ICitizenData data) {
        if (!McTalkingConfig.INSTANCE.instance().enableColonyStatsMentions) {
            return null;
        }

        IColony colony = data.getColony();
        if (colony == null) return null;

        var stats = colony.getStatisticsManager();
        String modPrefix = Constants.MOD_ID + ".";

        int kills = stats.getStatTotal(modPrefix + "killed_total");
        if (kills > THRESHOLD_KILLED) {
            return "Colony has fought off over " + THRESHOLD_KILLED + " mobs — shows how tough this place is.";
        }

        int builds = stats.getStatTotal(modPrefix + "built_total");
        if (builds > THRESHOLD_BUILDS) {
            return "Colony has built over " + THRESHOLD_BUILDS + " buildings — it's really growing.";
        }

        int raids = stats.getStatTotal(modPrefix + "raids_total");
        if (raids > THRESHOLD_RAIDS) {
            return "Colony has survived over " + THRESHOLD_RAIDS + " raids — we're still standing.";
        }

        int deaths = stats.getStatTotal(modPrefix + "deaths_total");
        if (deaths > THRESHOLD_DEATHS) {
            int recentDeaths = stats.getStatsInPeriod(modPrefix + "deaths_total",
                    Math.max(0, colony.getDay() - 7), colony.getDay());
            if (recentDeaths > 3) {
                return pick(
                        "Too many have died recently. The colony feels heavy with loss.",
                        "We've lost too many lately. It's hard to keep spirits up.",
                        "The recent deaths weigh on everyone."
                );
            }
            return "So many have died since the colony was founded. We remember them.";
        }

        int citizenCount = colony.getCitizenManager().getCurrentCitizenCount();
        if (citizenCount > THRESHOLD_CITIZENS) {
            return "There are over " + THRESHOLD_CITIZENS + " of us now — the colony is thriving.";
        }

        return null;
    }

    private static String pick(String... options) {
        return options[ThreadLocalRandom.current().nextInt(options.length)];
    }
}
