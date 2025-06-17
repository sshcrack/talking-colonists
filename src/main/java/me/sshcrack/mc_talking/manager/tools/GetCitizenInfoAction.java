package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.gson.properties.ObjectProperty;
import me.sshcrack.mc_talking.gson.properties.PrimitiveProperty;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class GetCitizenInfoAction extends FunctionAction {
    public GetCitizenInfoAction() {
        super(
                "get_citizen_info",
                "Get information about a citizen",
                new ObjectProperty(new HashMap<>() {{
                    put("citizen_name", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                }})
        );
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, JsonObject parameters) {
        var level = citizen.level();
        if (parameters == null || !parameters.has("citizen_name")) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", "Missing or invalid parameters.");
            return errorResponse;
        }

        var name = parameters.get("citizen_name").getAsString();

        var foundOpt = colony
                .getCitizenManager()
                .getCitizens()
                .stream().filter(e -> e.getName().equals(name))
                .findFirst();        if (foundOpt.isEmpty()) {
            var obj = new JsonObject();
            obj.addProperty("error", "Citizen not found.");

            return obj;
        }

        var found = foundOpt.get();
        var tag = found.serializeNBT();

        return tagToJson(tag);
    }
}
