package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGraveyard;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLibrary;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingNetherWorker;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingSchool;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.mc_talking.mixin.BuildingLibraryAccessor;
import me.sshcrack.mc_talking.mixin.BuildingNetherWorkerAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
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
        fillTypeSpecificInfo(matched, result, citizen);

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

    private static void fillWorkOrderInfo(IBuilding building, JsonObject result) {
        if (!(building instanceof BuildingBuilder) && !(building instanceof BuildingMiner)) return;

        boolean hasWorkOrder;
        IBuilderWorkOrder wo = null;
        if (building instanceof BuildingBuilder builder) {
            wo = builder.getWorkOrder();
        } else if (building instanceof BuildingMiner miner) {
            wo = miner.getWorkOrder();
        }


        hasWorkOrder = wo != null;
        result.addProperty("has_work_order", hasWorkOrder);

        if (wo != null) {
            result.addProperty("work_order_type", wo.getWorkOrderType().name());
            result.addProperty("target_name", wo.getDisplayName().getString());
            result.addProperty("target_level", wo.getTargetLevel());
            result.addProperty("current_level", wo.getCurrentLevel());
            result.addProperty("work_order_position", wo.getLocation().toShortString());
            result.addProperty("progress_stage", wo.getStage().name());
        }
    }

    private static void fillTypeSpecificInfo(IBuilding building, JsonObject result, AbstractEntityCitizen citizen) {
        switch (building) {
            case BuildingCook cook -> fillCookInfo(cook, result);
            case BuildingMiner miner -> {
                result.addProperty("max_depth", miner.getDepthLimit(citizen.level()));
                fillWorkOrderInfo(miner, result);
            }
            case BuildingFarmer farmer -> fillFarmerInfo(farmer, result);
            case BuildingBarracks barracks -> result.addProperty("tower_count", barracks.getTowers().size());
            case BuildingWareHouse warehouse -> fillWareHouseInfo(warehouse, result);
            case BuildingBuilder builder -> fillWorkOrderInfo(builder, result);
            case BuildingTownHall ignored -> fillTownHallInfo(building, result);
            case BuildingSchool school -> fillSchoolInfo(school, result);
            case BuildingLibrary library -> fillLibraryInfo(library, result);
            case BuildingNetherWorker nw -> fillNetherWorkerInfo(nw, result);
            case BuildingGraveyard gy -> fillGraveyardInfo(gy, result);
            case BuildingUniversity ignored -> fillUniversityInfo(building, result);
            default -> fillCraftingInfo(building, result);
        }
    }

    private static void fillCookInfo(BuildingCook cook, JsonObject result) {
        var menuModule = cook.getModule(BuildingModules.RESTAURANT_MENU);
        if (menuModule != null) {
            var menu = menuModule.getMenu();
            JsonArray items = new JsonArray();
            for (ItemStorage storage : menu) {
                var stack = storage.getItemStack();
                FoodProperties food = stack.getItem().getFoodProperties(stack, null);
                double foodValue = FoodUtils.getFoodValue(stack, food, 0);
                int tier = FoodUtils.getFoodTier(foodValue);
                items.add(stack.getDisplayName().getString() + " (tier " + tier + ", x" + storage.getAmount() + ")");
            }
            result.add("menu_items", items);
        }

        var workModule = cook.getModule(BuildingModules.COOK_WORK);
        boolean hasCook = workModule != null && workModule.hasAssignedCitizen();
        result.addProperty("has_assigned_cook", hasCook);
    }

    private static void fillFarmerInfo(BuildingFarmer farmer, JsonObject result) {
        JsonArray plantedCrops = new JsonArray();
        int fieldCount = 0;
        var extModules = farmer.getModules(BuildingExtensionsModule.class);
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
    }

    private static void fillWareHouseInfo(BuildingWareHouse warehouse, JsonObject result) {
        var containers = warehouse.getContainers();
        result.addProperty("container_count", containers != null ? containers.size() : 0);
    }

    private static void fillTownHallInfo(IBuilding building, JsonObject result) {
        var col = building.getColony();
        if (col != null) {
            result.addProperty("citizen_count", col.getCitizenManager().getCurrentCitizenCount());
            result.addProperty("max_citizens", col.getCitizenManager().getMaxCitizens());
            result.addProperty("overall_happiness", col.getOverallHappiness());
        }
    }

    private static void fillSchoolInfo(BuildingSchool school, JsonObject result) {
        var assigned = school.getAllAssignedCitizen();
        int studentCount = assigned != null ? assigned.size() : 0;
        result.addProperty("student_count", Math.max(0, studentCount - 1));
        result.addProperty("has_teacher", studentCount > 0);
    }

    private static void fillLibraryInfo(BuildingLibrary library, JsonObject result) {
        result.addProperty("bookshelves_count", ((BuildingLibraryAccessor) library).getBookCases().size());
    }

    private static void fillNetherWorkerInfo(BuildingNetherWorker nw, JsonObject result) {
        var accessor = (BuildingNetherWorkerAccessor) nw;
        int currentTrips = accessor.getCurrentTrips();
        int currentPeriodDay = accessor.getCurrentPeriodDay();
        int maxPerPeriod = BuildingNetherWorker.getMaxPerPeriod();
        int periodDays = BuildingNetherWorker.getPeriodDays();

        result.addProperty("is_ready_for_trip", nw.isReadyForTrip());
        result.addProperty("trips_this_period", currentTrips);
        result.addProperty("max_trips_per_period", maxPerPeriod);
        result.addProperty("current_period_day", currentPeriodDay);
        result.addProperty("period_days", periodDays);
        result.addProperty("days_until_reset", periodDays - currentPeriodDay);
        result.addProperty("portal_present", nw.getPortalLocation() != null);
        result.addProperty("close_portal_on_return", nw.shallClosePortalOnReturn());
    }

    private static void fillGraveyardInfo(BuildingGraveyard gy, JsonObject result) {
        var gravePositions = gy.getGravePositions();
        result.addProperty("grave_count", gravePositions != null ? gravePositions.size() : 0);
        var currentGrave = gy.getGraveToWorkOn();
        if (currentGrave != null) {
            result.addProperty("has_current_grave_task", true);
            result.addProperty("current_grave_position", currentGrave.toShortString());
        } else {
            result.addProperty("has_current_grave_task", false);
        }
    }

    private static void fillUniversityInfo(IBuilding building, JsonObject result) {
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

    private static void fillCraftingInfo(IBuilding building, JsonObject result) {
        var craftingModules = building.getModules(AbstractCraftingBuildingModule.class);
        if (craftingModules.isEmpty()) return;

        var recipeManager = IColonyManager.getInstance().getRecipeManager();
        var allRecipes = recipeManager.getRecipes();

        JsonArray recipesArray = new JsonArray();
        int totalRecipeCount = 0;
        final int MAX_DISPLAYED = 10;

        for (var module : craftingModules) {
            var tokens = module.getRecipes();
            for (var token : tokens) {
                totalRecipeCount++;
                if (recipesArray.size() >= MAX_DISPLAYED) continue;

                var storage = allRecipes.get(token);
                if (storage != null) {
                    var output = storage.getPrimaryOutput();
                    recipesArray.add(output.getDisplayName().getString());
                }
            }
        }

        result.addProperty("crafting_module_count", craftingModules.size());
        result.add("known_recipe_examples", recipesArray);
        result.addProperty("total_recipe_count", totalRecipeCount);
        if (totalRecipeCount > MAX_DISPLAYED) {
            result.addProperty("truncated_hint", "Only showing first " + MAX_DISPLAYED + " of " + totalRecipeCount + " recipes.");
        }
    }
}
