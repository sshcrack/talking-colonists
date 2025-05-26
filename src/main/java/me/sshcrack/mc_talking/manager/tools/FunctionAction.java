package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.gson.properties.Property;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FunctionAction {
    private final String name;
    private final String description;
    private final Property property;

    public FunctionAction(String name, String description) {
        this(name, description, null);
    }

    public FunctionAction(String name, String description, Property property) {
        this.name = name;
        this.description = description;
        this.property = property;
    }

    public Property getProperty() {
        return property;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @NotNull
    public abstract JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters);

    protected JsonObject tagToJson(CompoundTag tag) {
        return tagToJson(tag, Tag.TAG_COMPOUND).getAsJsonObject();
    }

    protected JsonElement tagToJson(Tag tag, byte type) {
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

}
