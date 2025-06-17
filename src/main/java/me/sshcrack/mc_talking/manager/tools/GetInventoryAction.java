package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
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
            ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(stack.getItem());
            itemObj.addProperty("registry_id", registryName != null ? registryName.toString() : "unknown");
            itemObj.addProperty("name", stack.getDisplayName().getString());
            itemObj.addProperty("count", stack.getCount());
            itemObj.addProperty("max_count", stack.getMaxStackSize());            // Get lore from NBT if it exists
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("display", 10)) {
                CompoundTag displayTag = tag.getCompound("display");
                if (displayTag.contains("Lore", 9)) {
                    ListTag loreTag = displayTag.getList("Lore", 8);
                    List<String> loreLines = new ArrayList<>();
                    for (int j = 0; j < loreTag.size(); j++) {
                        loreLines.add(loreTag.getString(j));
                    }
                    itemObj.addProperty("lore", String.join("\n", loreLines));
                }
            }

            obj.add("item_" + i, itemObj);
        }
        return obj;
    }
}
