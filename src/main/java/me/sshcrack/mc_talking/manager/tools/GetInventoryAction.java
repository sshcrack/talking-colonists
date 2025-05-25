package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

public class GetInventoryAction extends FunctionAction {
    public GetInventoryAction() {
        super("get_inventory", "Lists the items in your inventory.");
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, JsonObject parameters) {
        var obj = new JsonObject();

        var inv = citizen.getInventoryCitizen();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }

            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("slot", i);
            itemObj.addProperty("registry_id", stack.getItemHolder().getRegisteredName());
            itemObj.addProperty("name", stack.getDisplayName().getString());
            itemObj.addProperty("count", stack.getCount());
            itemObj.addProperty("max_count", stack.getMaxStackSize());

            var lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                itemObj.addProperty("lore", lore
                        .lines()
                        .stream()
                        .map(Component::getString)
                        .collect(Collectors.joining("\n"))
                );
            }

            obj.add("item_" + i, itemObj);
        }
        return obj;
    }
}
