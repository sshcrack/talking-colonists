package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.constant.Constants;
import me.sshcrack.mc_talking.gson.properties.ObjectProperty;
import me.sshcrack.mc_talking.gson.properties.PrimitiveProperty;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class GetColonyAction extends FunctionAction {
    public GetColonyAction() {
        super("get_colony", "Gets information about the colony. If no colony ID is provided, it returns the colony the citizen is currently living in",
                new ObjectProperty(new HashMap<>() {{
                    put("colony_id", new PrimitiveProperty(PrimitiveProperty.Type.INTEGER, false));
                }})
        );
    }

    /**
     * Collects relevant colony information into a JsonObject for roleplaying purposes.
     * Does not include specific citizen information.
     *
     * @param colony the colony to get information from
     * @return JsonObject containing colony information
     */
    public JsonObject getColonyInfoForRoleplaying(IColony colony) {
        final JsonObject colonyInfo = new JsonObject();

        // Basic colony information
        colonyInfo.addProperty("id", colony.getID());
        colonyInfo.addProperty("name", colony.getName());
        colonyInfo.addProperty("dimension", colony.getDimension().location().toString());
        colonyInfo.addProperty("day", colony.getDay());
        colonyInfo.addProperty("isDay", colony.isDay());
        colonyInfo.addProperty("isUnderAttack", colony.isColonyUnderAttack());
        colonyInfo.addProperty("isActive", colony.isActive());
        colonyInfo.addProperty("hasTownHall", colony.hasTownHall());
        colonyInfo.addProperty("hasWarehouse", colony.hasWarehouse());
        colonyInfo.addProperty("overallHappiness", colony.getOverallHappiness());
        colonyInfo.addProperty("citizenCount", colony.getCitizenManager().getCurrentCitizenCount());
        colonyInfo.addProperty("maxCitizens", colony.getCitizenManager().getMaxCitizens());
        colonyInfo.addProperty("structurePack", colony.getStructurePack());
        colonyInfo.addProperty("teamColor", colony.getTeamColonyColor().name());
        colonyInfo.addProperty("textureStyle", colony.getTextureStyleId());
        colonyInfo.addProperty("nameStyle", colony.getNameStyle());

        // Buildings information
        final JsonArray buildingsArray = new JsonArray();
        for (final IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            final JsonObject buildingObj = new JsonObject();
            buildingObj.addProperty("type", building.getBuildingType().getRegistryName().getPath());
            buildingObj.addProperty("level", building.getBuildingLevel());
            buildingObj.addProperty("position", building.getPosition().toShortString());
            buildingsArray.add(buildingObj);
        }
        colonyInfo.add("buildings", buildingsArray);

        // Research information
        final JsonObject researchObj = new JsonObject();
        // Using getCompletedList() instead of getCompletedResearch()
        List<ResourceLocation> completedResearchList = colony.getResearchManager().getResearchTree().getCompletedList();
        researchObj.addProperty("completedResearchCount", completedResearchList.size());

        final JsonArray completedResearch = new JsonArray();
        for (final ResourceLocation research : completedResearchList) {
            completedResearch.add(research.toString());
        }
        researchObj.add("completedResearch", completedResearch);
        colonyInfo.add("research", researchObj);

        // Statistics information
        final JsonObject statsObj = new JsonObject();
        statsObj.addProperty("raidCount", colony.getStatisticsManager().getStatTotal(Constants.MOD_ID + ".raids_total"));

        // Add other useful statistics if available
        for (String statType : colony.getStatisticsManager().getStatTypes()) {
            // Filter out statistics that might be useful for roleplaying
            if (statType.contains("raid") ||
                    statType.contains("death") ||
                    statType.contains("built") ||
                    statType.contains("killed") ||
                    statType.contains("produced")) {
                statsObj.addProperty(statType, colony.getStatisticsManager().getStatTotal(statType));
            }
        }
        colonyInfo.add("statistics", statsObj);

        // Current event information
        final JsonObject eventsObj = new JsonObject();
        eventsObj.addProperty("currentEventCount", colony.getEventManager().getEvents().size());
        colonyInfo.add("events", eventsObj);

        // Add waypoints count
        colonyInfo.addProperty("waypointCount", colony.getWayPoints().size());

        return colonyInfo;
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony currColony, @Nullable JsonObject parameters) {
        var id = parameters != null && parameters.has("colony_id")
                ? parameters.get("colony_id").getAsInt()
                : currColony.getID();

        var colony = IColonyManager.getInstance().getColonyByWorld(id, citizen.level());
        if(colony == null) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("error", "Colony not found with ID: " + id);
            return errorResponse;
        }

        return getColonyInfoForRoleplaying(colony);
    }
}
