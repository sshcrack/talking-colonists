package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.gson.properties.ObjectProperty;
import me.sshcrack.mc_talking.gson.properties.PrimitiveProperty;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class GetCitizenInfoAction extends FunctionAction {
    public GetCitizenInfoAction() {
        super(
                "get_citizen_info",
                "Get information about a citizen",
                new ObjectProperty(new HashMap<>() {{
                    put("citizen_uuid", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                }})
        );
    }

    private JsonElement tagToJson(Tag tag, byte type) {
        switch (type) {
            case CompoundTag.TAG_COMPOUND -> {
                var compound = (CompoundTag) tag;
                var obj = new JsonObject();
                for (String key : compound.getAllKeys()) {
                    var value = tagToJson(compound.get(key), compound.getTagType(key));
                    obj.add(key, value);
                }

                return obj;
            }
            case CompoundTag.TAG_LIST -> {
                var list = (ListTag) tag;
                var jsonArray = new com.google.gson.JsonArray();
                for (Tag element : list) {
                    jsonArray.add(tagToJson(element, element.getId()));
                }
                return jsonArray;
            }
            case CompoundTag.TAG_STRING -> {
                return new JsonPrimitive(tag.getAsString());
            }
            case CompoundTag.TAG_INT -> {
                return new JsonPrimitive(((NumericTag) tag).getAsInt());
            }
            case CompoundTag.TAG_LONG -> {
                return new JsonPrimitive(((NumericTag) tag).getAsLong());
            }
            case CompoundTag.TAG_DOUBLE -> {
                return new JsonPrimitive(((NumericTag) tag).getAsDouble());
            }
            case CompoundTag.TAG_BYTE -> {
                return new JsonPrimitive(((NumericTag) tag).getAsByte());
            }
            case CompoundTag.TAG_SHORT -> {
                return new JsonPrimitive(((NumericTag) tag).getAsShort());
            }
        }

        return JsonNull.INSTANCE; // Fallback for unsupported types
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, JsonObject parameters) {
        var level = citizen.level();
        if (parameters == null || !parameters.has("citizen_uuid")) {
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("error", "Missing or invalid parameters.");
            return errorResponse;
        }

        var uuid_str = parameters.get("citizen_uuid").getAsString();
        var uuid = UUID.fromString(uuid_str);

        var foundOpt = colony
                .getCitizenManager()
                .getCitizens()
                .stream().filter(e -> e.getUUID().equals(uuid))
                .findFirst();

        if (foundOpt.isEmpty()) {
            var obj = new JsonObject();
            obj.addProperty("error", "Citizen not found.");

            return obj;
        }

        var found = foundOpt.get();
        var tag = found.serializeNBT(level.registryAccess());

        return tagToJson(tag, Tag.TAG_COMPOUND).getAsJsonObject();
    }
}
