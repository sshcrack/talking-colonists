package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.util.ResearchState;
import net.minecraft.network.chat.Component;

/** The research a university has in progress, with time elapsed and remaining, for {@link DescribeBuildingAction}. */
final class ResearchDetails {
    private ResearchDetails() {
    }

    static void fillUniversityInfo(IBuilding building, JsonObject result) {
        var col = building.getColony();
        if (col == null) return;

        var researchTree = col.getResearchManager().getResearchTree();
        var inProgress = researchTree.getResearchInProgress();
        result.addProperty("research_in_progress_count", inProgress.size());
        if (inProgress.isEmpty()) return;

        JsonArray researchArray = new JsonArray();
        for (ILocalResearch research : inProgress) {
            researchArray.add(buildResearchObject(research));
        }
        result.add("research_in_progress", researchArray);
    }

    private static JsonObject buildResearchObject(ILocalResearch research) {
        JsonObject rObj = new JsonObject();
        var globalTree = IGlobalResearchTree.getInstance();
        var global = globalTree.getResearch(research.getBranch(), research.getId());
        if (global != null) {
            var nameContents = global.getName();
            rObj.addProperty("name", Component.translatable(nameContents.getKey(), nameContents.getArgs()).getString());
        } else {
            rObj.addProperty("name", research.getId().toString());
        }
        rObj.addProperty("branch", research.getBranch().toString());
        rObj.addProperty("state", research.getState().name());
        rObj.addProperty("progress_ticks", research.getProgress());

        if (research.getState() == ResearchState.IN_PROGRESS) {
            addResearchProgress(research, rObj, globalTree);
        }
        return rObj;
    }

    private static void addResearchProgress(ILocalResearch research, JsonObject rObj, IGlobalResearchTree globalTree) {
        var branchData = globalTree.getBranchData(research.getBranch());
        if (branchData == null) return;

        int totalTicks = branchData.getBaseTime(research.getDepth());
        int elapsed = research.getProgress();
        int remaining = Math.max(0, totalTicks - elapsed);
        rObj.addProperty("total_ticks", totalTicks);
        rObj.addProperty("time_elapsed", formatResearchTime(elapsed));
        rObj.addProperty("estimated_time_remaining", formatResearchTime(remaining));
        rObj.addProperty("completion_percentage", Math.min(100, (elapsed * 100) / Math.max(1, totalTicks)));
    }

    private static String formatResearchTime(int ticks) {
        int totalSeconds = ticks * 25;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
