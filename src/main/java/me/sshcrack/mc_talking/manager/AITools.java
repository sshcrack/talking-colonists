package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup;
import me.sshcrack.mc_talking.gson.BidiGenerateContentSetup.Tool.FunctionDeclaration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_ID;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_SITTING;

public class AITools {
    private static final String TAG_RECRUIT_COST = "rcost";
    private static final String TAG_RECRUIT_COST_QTY = "rcostqty";
    public static final HashMap<String, FunctionAction> registeredFunctions = new HashMap<>();


    public static void add(FunctionAction action) {
        registeredFunctions.put(action.tool().name, action);
    }

    public record FunctionAction(FunctionDeclaration tool, Consumer<AbstractEntityCitizen> action) {
        public FunctionAction(FunctionDeclaration tool, Consumer<AbstractEntityCitizen> action) {
            this.tool = tool;
            this.action = action;

            add(this);
        }
    }

    @SuppressWarnings("unused")
    public static FunctionAction LEAVE_ACTION = new FunctionAction(
            new FunctionDeclaration("leave_colony", "You leave the colony. Only leave the colony when you are REALLY upset and sure you NEVER want to come back."),
            citizen -> {
                var colony = citizen.getCitizenColonyHandler().getColony();
                var citizenManager = colony.getCitizenManager();
                var visitorManager = colony.getVisitorManager();

                var building = colony.getBuildingManager().getFirstBuildingMatching(e -> e.getBuildingType().equals(ModBuildings.tavern.get()));
                if (building == null) {
                    System.out.println("Tavern not found, cannot leave colony.");
                    return;
                }

                var module = building.getFirstModuleOccurance(TavernBuildingModule.class);
                if (module == null) {
                    System.out.println("Tavern module not found, cannot leave colony.");
                    return;
                }

                var data = citizen.getCitizenData();
                var level = citizen.level();
                var pos = citizen.blockPosition();

                // First, serialize all citizen data to NBT
                CompoundTag citizenTag = data.serializeNBT(level.registryAccess());

                // Save ID before removing citizen (we'll need it to clean up references)
                int oldId = data.getId();

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
            }
    );

    @SuppressWarnings("unused")
    public static FunctionAction HELLO_ACTION = new FunctionAction(
            new FunctionDeclaration("say_hello", "You greet the manager."),
            citizen -> {
            }
    );

    public static List<BidiGenerateContentSetup.Tool> getAllTools() {
        var list = new ArrayList<BidiGenerateContentSetup.Tool>();

        var tool = new BidiGenerateContentSetup.Tool();
        tool.functionDeclarations.addAll(
                registeredFunctions
                        .values()
                        .stream()
                        .map(FunctionAction::tool)
                        .toList()
        );

        list.add(tool);
        return list;
    }

    public static void register() {
    }
}
