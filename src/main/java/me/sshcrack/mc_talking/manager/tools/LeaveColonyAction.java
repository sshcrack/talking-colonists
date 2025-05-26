package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_SITTING;

public class LeaveColonyAction extends FunctionAction {
    private static final String TAG_RECRUIT_COST = "rcost";
    private static final String TAG_RECRUIT_COST_QTY = "rcostqty";

    public LeaveColonyAction() {
        super("leave_colony", "You leave the colony and become a visitor. Use this ONLY if you are REALLY unhappy and the manager doesn't care about you.");
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, JsonObject parameters) {
        var citizenManager = colony.getCitizenManager();
        var visitorManager = colony.getVisitorManager();


        var building = colony.getBuildingManager().getFirstBuildingMatching(e -> e.getBuildingType().equals(ModBuildings.tavern.get()));
        if (building == null) {
            var invalidReturn = new JsonObject();
            invalidReturn.addProperty("error", "No tavern found in the colony.");
            return invalidReturn;
        }

        var module = building.getFirstModuleOccurance(TavernBuildingModule.class);
        if (module == null) {
            var invalidReturn = new JsonObject();
            invalidReturn.addProperty("error", "No tavern found in the colony.");
            return invalidReturn;
        }

        var data = citizen.getCitizenData();
        var level = citizen.level();
        var pos = citizen.blockPosition();

        // First, serialize all citizen data to NBT
        CompoundTag citizenTag = data.serializeNBT(level.registryAccess());

        // Create recruitment cost
        final ItemStack recruitCost = Items.DIAMOND.getDefaultInstance();
        recruitCost.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("mc_talking.recruit_lore"))));
        recruitCost.setCount(16);

        // Remove the original citizen
        citizenManager.removeCivilian(data);
        citizen.remove(Entity.RemovalReason.DISCARDED);

        // Create a new visitor with proper registration
        IVisitorData visitorData = (IVisitorData) visitorManager.createAndRegisterCivilianData();

        // Clean up the NBT to remove job data and add visitor-specific fields
        citizenTag.remove("job");  // Remove job data as visitors don't have jobs

        // Add visitor-specific data
        citizenTag.put(TAG_RECRUIT_COST, recruitCost.save(level.registryAccess()));  // Using save with no arguments
        citizenTag.putInt(TAG_RECRUIT_COST_QTY, recruitCost.getCount());
        BlockPosUtil.write(citizenTag, TAG_SITTING, BlockPos.ZERO);

        // Update the ID in the tag to match the new visitor ID
        citizenTag.putInt(TAG_ID, visitorData.getId());

        // Deserialize the modified NBT into the visitor
        visitorData.deserializeNBT(level.registryAccess(), citizenTag);

        // Explicitly set home building (important for visitor functionality)
        visitorData.setHomeBuilding(building);

        // Spawn the visitor at the citizen's location
        visitorManager.spawnOrCreateCivilian(visitorData, level, pos, true);

        // Add to tavern's external citizens list - critical for recruitment to work
        module.getExternalCitizens().add(visitorData.getId());

        // Add recruitment interaction
        visitorData.triggerInteraction(new RecruitmentInteraction(
                Component.translatable("com.minecolonies.coremod.gui.chat.recruitstory" +
                                (level.random.nextInt(5) + 1),
                        visitorData.getName().split(" ")[0]),
                ChatPriority.IMPORTANT));

        var obj = new JsonObject();
        obj.addProperty("success", true);

        return obj;
    }
}
