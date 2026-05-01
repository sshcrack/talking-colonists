package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipChangeType;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

public class RecordRelationshipChange extends FunctionAction {
    public RecordRelationshipChange() {
        super("record_relationship_change", """
                        Updates your emotional feelings and relationship to the given citizen in your memory,
                        so you can remember it later.
                        The change must be between -1.0 and 1.0.
                        """,
                new ObjectProperty(new HashMap<>() {{
                    put("citizen_name", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                    put("change", new PrimitiveProperty(PrimitiveProperty.Type.NUMBER, true));
                    put("type", new EnumProperty(Arrays.stream(CitizenRelationshipChangeType.values())
                            .map(Enum::name)
                            .toList(),
                            true
                    ));
                }}));
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var obj = new JsonObject();
        if (parameters == null || !parameters.has("event")) {
            obj.addProperty("success", false);
            obj.addProperty("error", "Missing or invalid parameters.");
            return obj;
        }

        var citizenName = parameters.get("citizen_name").getAsString();
        var change = parameters.get("change").getAsFloat();
        var typeStr = parameters.get("type").getAsString();

        var typeOpt = Arrays.stream(CitizenRelationshipChangeType.values())
                .filter(t -> t.name().equals(typeStr))
                .findFirst();

        if (typeOpt.isEmpty()) {
            obj.addProperty("success", false);
            obj.addProperty("error", "Invalid relationship change type.");
            return obj;
        }

        var type = typeOpt.get();

        if (change < -1.0 || change > 1.0) {
            obj.addProperty("success", false);
            obj.addProperty("error", "Change must be between -1.0 and 1.0.");
            return obj;
        }

        var target = colony.getCitizenManager().getCitizens().stream()
                .filter(c -> c.getName().equals(citizenName))
                .findFirst();

        if (target.isEmpty()) {
            obj.addProperty("success", false);
            obj.addProperty("error", "Target citizen not found.");
            return obj;
        }


        var memory = ((CitizenDataMemoryExtended) citizen.getCitizenData()).mc_talking$getOrInitializeMemory();

        memory.addRelationshipChange(target.get().getUUID(), type, change);
        return obj;
    }
}
