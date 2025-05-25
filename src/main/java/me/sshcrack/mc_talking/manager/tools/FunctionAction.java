package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.gson.properties.Property;
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
}
