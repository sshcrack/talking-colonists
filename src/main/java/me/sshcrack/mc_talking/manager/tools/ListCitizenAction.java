package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ListCitizenAction extends FunctionAction {
    public ListCitizenAction() {
        super("list_citizens", "Listens all citizens in the colony");
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var obj = new JsonObject();
        var arr = new JsonArray();

        for (ICitizenData c : colony.getCitizenManager().getCitizens()) {
            arr.add(c.getName());
        }

        obj.add("citizens", arr);
        return obj;
    }
}
