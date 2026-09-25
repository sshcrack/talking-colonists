package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class DescribeBuildingAction extends GeneralFunctionAction {

    private static final String OWN_BUILDING_IDENTIFIER = "own_building";
    private static final List<String> BUILDING_TYPES = List.of(
            "cook", "miner", "farmer", "townhall", "barracks", "warehouse",
            "library", "school", "builder", "residence", "deliveryman",
            "tavern", "hospital", "enchanter", "smeltery", "composter",
            "baker", "fisherman", "lumberjack", "shepherd", "cowboy",
            "graveyard", "plantation", "beekeeper", "mechanic", "sifter",
            "crusher", "netherworker", "florist", "archery", "combatacademy",
            "rabbithutch", OWN_BUILDING_IDENTIFIER
    );

    public DescribeBuildingAction() {
        super("describe_building",
                "Describes a colony building in detail: its type, level, assigned workers, and type-specific " +
                        "information (e.g. restaurant menu items, mine depth, farm crops, builder projects, etc.). " +
                        "If multiple buildings of the same type exist, the nearest one to you is described. " +
                        "(\"own_building\" describes the building you work at.)",
                new ObjectProperty(new HashMap<>() {{
                    put("type", new EnumProperty(BUILDING_TYPES, true));
                }})
        );
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        JsonObject result = new JsonObject();

        if (parameters == null || !parameters.has("type")) {
            result.addProperty("error", "You must provide a building type.");
            return result;
        }

        String query = parameters.get("type").getAsString().trim().toLowerCase();

        IBuilding matched = findBuilding(colony, query, citizen);
        if (matched == null) {
            if (query.equals(OWN_BUILDING_IDENTIFIER)) {
                result.addProperty("error", "You are not assigned to any building.");
            } else {
                result.addProperty("error", "No building found matching '" + query + "'.");
                result.addProperty("hint", "This colony may not have that building type. Use get_colony to list all buildings.");
            }
            return result;
        }

        fillBasicInfo(matched, result);
        BuildingDetails.fill(matched, result, citizen);

        return result;
    }

    @Nullable
    private IBuilding findBuilding(IColony colony, String query, AbstractEntityCitizen citizen) {
        if (query.equals("own_building")) {
            var data = citizen.getCitizenData();
            if (data == null) return null;
            return data.getWorkBuilding();
        }

        var bm = colony.getServerBuildingManager();
        if (bm == null || query.isEmpty()) return null;

        var citizenPos = citizen.blockPosition();
        IBuilding nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var b : bm.getBuildings().values()) {
            if (b.getBuildingType().getRegistryName().getPath().equals(query)) {
                double dist = citizenPos.distSqr(b.getPosition());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = b;
                }
            }
        }
        return nearest;
    }

    private static void fillBasicInfo(IBuilding building, JsonObject result) {
        String name = building.getBuildingDisplayName();
        if (name.contains(".") || name.contains("/")) {
            name = Component.translatable(building.getBuildingType().getTranslationKey()).getString();
        }
        result.addProperty("name", name);
        result.addProperty("type", building.getBuildingType().getRegistryName().getPath());
        result.addProperty("level", building.getBuildingLevel());
        result.addProperty("position", building.getPosition().toShortString());
        result.addProperty("is_pending_construction", building.isPendingConstruction());
        result.addProperty("is_built", building.isBuilt());

        var citizens = building.getAllAssignedCitizen();
        if (citizens != null && !citizens.isEmpty()) {
            JsonArray names = new JsonArray();
            for (ICitizenData cd : citizens) {
                names.add(cd.getName());
            }
            result.add("assigned_citizens", names);
        }
    }
}
