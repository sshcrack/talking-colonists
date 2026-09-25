package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IBuilderWorkOrder;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
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
import me.sshcrack.mc_talking.mixin.BuildingLibraryAccessor;
import me.sshcrack.mc_talking.mixin.BuildingNetherWorkerAccessor;
import net.minecraft.world.food.FoodProperties;

/** The type-specific part of {@link DescribeBuildingAction}: a restaurant's menu, a mine's depth, a farm's crops, ... */
final class BuildingDetails {
    private BuildingDetails() {
    }

    static void fill(IBuilding building, JsonObject result, AbstractEntityCitizen citizen) {
        if (building instanceof BuildingCook cook) {
            fillCookInfo(cook, result);
        } else if (building instanceof BuildingMiner miner) {
            result.addProperty("max_depth", miner.getDepthLimit(citizen.level()));
            fillWorkOrderInfo(miner, result);
        } else if (building instanceof BuildingFarmer farmer) {
            fillFarmerInfo(farmer, result);
        } else if (building instanceof BuildingBarracks barracks) {
            result.addProperty("tower_count", barracks.getTowers().size());
        } else if (building instanceof BuildingWareHouse warehouse) {
            fillWareHouseInfo(warehouse, result);
        } else if (building instanceof BuildingBuilder builder) {
            fillWorkOrderInfo(builder, result);
        } else if (building instanceof BuildingTownHall) {
            fillTownHallInfo(building, result);
        } else if (building instanceof BuildingSchool school) {
            fillSchoolInfo(school, result);
        } else if (building instanceof BuildingLibrary library) {
            fillLibraryInfo(library, result);
        } else if (building instanceof BuildingNetherWorker nw) {
            fillNetherWorkerInfo(nw, result);
        } else if (building instanceof BuildingGraveyard gy) {
            fillGraveyardInfo(gy, result);
        } else if (building instanceof BuildingUniversity) {
            ResearchDetails.fillUniversityInfo(building, result);
        } else {
            fillCraftingInfo(building, result);
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
            result.addProperty("note", "The target name is a internal name. Interpret the name accordingly.");
        }
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
