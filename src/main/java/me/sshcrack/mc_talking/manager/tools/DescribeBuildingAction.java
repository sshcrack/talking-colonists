package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IAnimalManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class DescribeBuildingAction extends GeneralFunctionAction {
    public DescribeBuildingAction() {
        super("describe_building", "Provides detailed information about a specific building type in the colony. Query types: 'stable' for cavalry horse information.",
                new ObjectProperty(new HashMap<>() {{
                    put("type", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                }})
        );
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        if (parameters == null || !parameters.has("type")) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing required parameter: type");
            return error;
        }
        String type = parameters.get("type").getAsString();
        if ("stable".equals(type)) {
            return describeStable(colony);
        }
        JsonObject error = new JsonObject();
        error.addProperty("error", "Unknown building type: " + type);
        return error;
    }

    private static @NotNull JsonObject describeStable(@Nullable IColony colony) {
        JsonObject result = new JsonObject();
        if (colony == null) {
            result.addProperty("totalAnimals", 0);
            result.addProperty("message", "No colony available.");
            return result;
        }
        IAnimalManager mgr = colony.getAnimalManager();
        if (mgr == null || mgr.getCurrentAnimalCount() == 0) {
            result.addProperty("totalAnimals", 0);
            result.addProperty("message", "The colony does not have any managed animals in stables.");
            return result;
        }
        result.addProperty("totalAnimals", mgr.getCurrentAnimalCount());
        JsonArray animals = new JsonArray();
        for (IAnimalData animal : mgr.getAnimals()) {
            JsonObject a = new JsonObject();
            a.addProperty("id", animal.getId());
            a.addProperty("uuid", animal.getUUID().toString());
            IBuilding home = animal.getHomeBuilding();
            if (home != null) {
                a.addProperty("homeBuilding", home.getBuildingDisplayName());
            }
            BlockPos pos = animal.getLastPosition();
            if (pos != null) {
                a.addProperty("position", pos.toShortString());
            }
            a.addProperty("combatCooldown", animal.getCombatCooldown());
            animals.add(a);
        }
        result.add("animals", animals);
        return result;
    }
}
