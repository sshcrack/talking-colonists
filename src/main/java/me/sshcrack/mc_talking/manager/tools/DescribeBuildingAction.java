package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLibrary;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSchool;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.api.colony.ICitizenData;
import me.sshcrack.mc_talking.mixin.BuildingLibraryAccessor;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public class DescribeBuildingAction extends GeneralFunctionAction {

    private static final List<String> BUILDING_TYPES = List.of(
        "cook", "miner", "farmer", "townhall", "barracks", "warehouse",
        "library", "school", "builder", "residence", "deliveryman",
        "tavern", "hospital", "enchanter", "smeltery", "composter",
        "baker", "fisherman", "lumberjack", "shepherd", "cowboy",
        "graveyard", "plantation", "beekeeper", "mechanic", "sifter",
        "crusher", "netherworker", "florist", "archery", "combatacademy",
        "rabbithutch"
    );

    public DescribeBuildingAction() {
        super("describe_building",
            "Describes a colony building in detail: its type, level, assigned workers, and type-specific " +
            "information (e.g. restaurant menu items, mine depth, farm crops, etc.). If multiple buildings of the " +
            "same type exist, the nearest one to you is described.",
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

        IBuilding matched = findBuilding(colony, query);
        if (matched == null) {
            result.addProperty("error", "No building found matching '" + query + "'.");
            result.addProperty("hint", "This colony may not have that building type. Use get_colony to list all buildings.");
            return result;
        }

        fillBasicInfo(matched, result);
        fillTypeSpecificInfo(matched, result, citizen);

        return result;
    }

    @Nullable
    private IBuilding findBuilding(IColony colony, String query) {
        var bm = colony.getServerBuildingManager();
        if (bm == null || query.isEmpty()) return null;

        for (var b : bm.getBuildings().values()) {
            if (b.getBuildingType().getRegistryName().getPath().equals(query)) {
                return b;
            }
        }
        return null;
    }

    private static void fillBasicInfo(IBuilding building, JsonObject result) {
        String name = building.getBuildingDisplayName();
        if (name == null || name.contains(".") || name.contains("/")) {
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

    private static void fillTypeSpecificInfo(IBuilding building, JsonObject result, AbstractEntityCitizen citizen) {
        var typePath = building.getBuildingType().getRegistryName().getPath();

        if (building instanceof BuildingCook cook) {
            var menuModule = cook.getModule(BuildingModules.RESTAURANT_MENU);
            if (menuModule != null) {
                var menu = menuModule.getMenu();
                JsonArray items = new JsonArray();
                for (var storage : menu) {
                    items.add(storage.getItemStack().getDisplayName().getString() + " (x" + storage.getAmount() + ")");
                }
                result.add("menu_items", items);
            }

            var workModule = cook.getModule(BuildingModules.COOK_WORK);
            boolean hasCook = workModule != null && workModule.hasAssignedCitizen();
            result.addProperty("has_assigned_cook", hasCook);

        } else if (building instanceof BuildingMiner miner) {
            Level level = citizen.level();
            result.addProperty("max_depth", miner.getDepthLimit(level));

        } else if (building instanceof BuildingFarmer farmer) {
            JsonArray plantedCrops = new JsonArray();
            int fieldCount = 0;
            var extModules = farmer.getModulesByType(BuildingExtensionsModule.class);
            for (BuildingExtensionsModule module : extModules) {
                for (IBuildingExtension ext : module.getOwnedExtensions()) {
                    if (ext instanceof FarmField field) {
                        fieldCount++;
                        if (!field.getSeed().isEmpty()) {
                            plantedCrops.add(field.getSeed().getDisplayName().getString());
                        }
                    }
                }
            }
            result.addProperty("field_count", fieldCount);
            result.add("planted_crops", plantedCrops);

        } else if (building instanceof BuildingBarracks barracks) {
            result.addProperty("tower_count", barracks.getTowers().size());

        } else if (building instanceof BuildingWareHouse warehouse) {
            var containers = warehouse.getContainers();
            result.addProperty("container_count", containers != null ? containers.size() : 0);

        } else if (building instanceof BuildingBuilder) {
            result.addProperty("mode", typePath);

        } else if ("townhall".equals(typePath)) {
            var col = building.getColony();
            if (col != null) {
                result.addProperty("citizen_count", col.getCitizenManager().getCurrentCitizenCount());
                result.addProperty("max_citizens", col.getCitizenManager().getMaxCitizens());
                result.addProperty("overall_happiness", col.getOverallHappiness());
            }

        } else if (building instanceof BuildingSchool school) {
            var assigned = school.getAllAssignedCitizen();
            int studentCount = assigned != null ? assigned.size() : 0;
            result.addProperty("student_count", Math.max(0, studentCount - 1));
            result.addProperty("has_teacher", studentCount > 0);

        } else if (building instanceof BuildingLibrary library) {
            result.addProperty("bookshelves_count", ((BuildingLibraryAccessor) library).getBookCases().size());
        }
    }

}
