package me.sshcrack.mc_talking.util;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.util.constant.Constants;
import me.sshcrack.mc_talking.config.McTalkingConfig;

import java.util.ArrayList;
import java.util.List;

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
     * <p>
     * Collects all eligible milestones and picks one at random, so no single
     * stat permanently dominates the dialogue.
     */
    public static String getColonyMilestoneText(ICitizenData data) {
        if (!McTalkingConfig.INSTANCE.instance().enableColonyStatsMentions) {
            return null;
        }

        IColony colony = data.getColony();
        if (colony == null) return null;

        var stats = colony.getStatisticsManager();
        String modPrefix = Constants.MOD_ID + ".";

        List<String> eligible = new ArrayList<>();

        int kills = stats.getStatTotal(modPrefix + "killed_total");
        if (kills > THRESHOLD_KILLED) {
            eligible.add("Colony has fought off over " + kills + " mobs — shows how tough this place is.");
        }

        int builds = stats.getStatTotal(modPrefix + "built_total");
        if (builds > THRESHOLD_BUILDS) {
            eligible.add("Colony has built over " + builds + " buildings — it's really growing.");
        }

        int raids = stats.getStatTotal(modPrefix + "raids_total");
        if (raids > THRESHOLD_RAIDS) {
            eligible.add("Colony has survived over " + raids + " raids — we're still standing.");
        }

        int deaths = stats.getStatTotal(modPrefix + "deaths_total");
        if (deaths > THRESHOLD_DEATHS) {
            int recentDeaths = stats.getStatsInPeriod(modPrefix + "deaths_total",
                    Math.max(0, colony.getDay() - 7), colony.getDay());
            if (recentDeaths > 3) {
                eligible.add("Too many have died recently. The colony feels heavy with loss.");
                eligible.add("We've lost too many lately. It's hard to keep spirits up.");
                eligible.add("The recent deaths weigh on everyone.");
            } else {
                eligible.add("So many have died since the colony was founded. We remember them.");
            }
        }

        int citizenCount = colony.getCitizenManager().getCurrentCitizenCount();
        if (citizenCount > THRESHOLD_CITIZENS) {
            eligible.add("There are over " + citizenCount + " of us now — the colony is thriving.");
        }

        if (eligible.isEmpty()) return null;
        return MiscUtil.pick(eligible.toArray(new String[0]));
    }
}
