package me.sshcrack.mc_talking.manager.prompt;

import com.minecolonies.api.colony.ColonyState;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.CitizenData;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
import me.sshcrack.mc_talking.api.prompt.view.CitizenEquipmentView;
import me.sshcrack.mc_talking.api.prompt.view.CitizenHousingStatus;
import me.sshcrack.mc_talking.api.prompt.view.ColonyFoodSituation;
import me.sshcrack.mc_talking.api.prompt.view.ObservationState;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Builds the needs part of the prompt context (moved from CitizenPromptViewFactory). */
public final class CitizenNeedsViews {
    private CitizenNeedsViews() {
    }

    @Nullable
    public static ColonyFoodSituation extractFoodSituation(ICitizenData data, CitizenActivityCategory activityCategory) {
        if (data.getSaturation() > 5.0) return null;
        if (activityCategory == CitizenActivityCategory.EATING) return ColonyFoodSituation.ALREADY_EATING;

        var colony = data.getColony();
        var bm = colony.getServerBuildingManager();
        var origin = data.getEntity()
                .map(e -> e.blockPosition())
                .orElseGet(() -> data.getWorkBuilding() != null
                        ? data.getWorkBuilding().getPosition()
                        : BlockPos.ZERO);

        BlockPos best = bm.getBestBuilding(origin, BuildingCook.class);
        if (best == null) return ColonyFoodSituation.NO_RESTAURANT;

        IBuilding rest = bm.getBuilding(best);
        if (rest == null) return ColonyFoodSituation.NO_RESTAURANT;

        boolean staffed = rest.getModule(BuildingModules.COOK_WORK).hasAssignedCitizen();
        return staffed ? ColonyFoodSituation.STAFFED_RESTAURANT : ColonyFoodSituation.UNSTAFFED_RESTAURANT;
    }

    @Nullable
    static String deriveRestaurantContext(ICitizenData data) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) return null;
        if (!(entityOpt.get() instanceof EntityCitizen citizen)) return null;
        var colony = data.getColony();
        if (colony == null) return null;
        var bm = colony.getServerBuildingManager();
        if (bm == null) return null;
        var origin = citizen.blockPosition();
        var best = bm.getBestBuilding(origin, BuildingCook.class);
        if (best == null) return null;
        var rest = bm.getBuilding(best);
        if (!(rest instanceof BuildingCook cook)) return null;
        String buildingName = Component.translatable(cook.getBuildingType().getTranslationKey()).getString();
        var cookModule = cook.getModule(BuildingModules.COOK_WORK);
        String cookName = null;
        if (cookModule != null && cookModule.hasAssignedCitizen()) {
            var citizens = cook.getAllAssignedCitizen();
            if (citizens != null && !citizens.isEmpty()) {
                cookName = citizens.iterator().next().getName();
            }
        }
        if (cookName != null) {
            return buildingName + " (" + cookName + ")";
        }
        return buildingName;
    }

    @Nullable
    static String deriveHomeName(ICitizenData data) {
        var home = data.getHomeBuilding();
        if (home == null) return null;
        String displayName = home.getBuildingDisplayName();
        if (displayName != null && !displayName.isEmpty() && !displayName.contains(".") && !displayName.contains("/")) {
            return displayName;
        }
        return Component.translatable(home.getBuildingType().getTranslationKey()).getString();
    }

    @Nullable
    static String deriveDiseaseName(ICitizenData data) {
        var handler = data.getCitizenDiseaseHandler();
        if (handler == null) return null;
        var disease = handler.getDisease();
        if (disease == null) return null;
        return disease.name().getString();
    }

    @Nullable
    static String deriveDeceasedName(ICitizenData data) {
        var mournHandler = data.getCitizenMournHandler();
        if (mournHandler == null) return null;
        var deceased = mournHandler.getDeceasedCitizens();
        if (deceased == null || deceased.isEmpty()) return null;
        return deceased.iterator().next();
    }

    public static ObservedValue<Double> extractObservedHealth(ICitizenData data, long gameTime) {
        var entityOpt = data.getEntity();
        if (entityOpt.isEmpty()) {
            return ObservedValue.unavailable(ObservationState.UNLOADED, gameTime);
        }
        var entity = entityOpt.get();
        return ObservedValue.current(
                (entity.getHealth() / Math.max(1.0, entity.getMaxHealth())) * 100.0,
                gameTime
        );
    }

    public static CitizenHousingStatus extractHousingStatus(ICitizenData data) {
        IBuilding home = data.getHomeBuilding();
        if (home != null) return CitizenHousingStatus.HOUSED;
        if (data.getColony().getState() == ColonyState.UNLOADED) return CitizenHousingStatus.UNKNOWN;
        return CitizenHousingStatus.HOMELESS;
    }

    public static ObservedValue<CitizenEquipmentView> extractEquipment(ICitizenData data, long gameTime) {
        if (!(data instanceof CitizenData concrete)) {
            return ObservedValue.unavailable(ObservationState.UNAVAILABLE, gameTime);
        }
        var inventory = concrete.getInventory();
        if (inventory == null) {
            return ObservedValue.unavailable(ObservationState.UNAVAILABLE, gameTime);
        }
        List<String> armor = new ArrayList<>();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = inventory.getArmorInSlot(slot);
            if (stack != null && !stack.isEmpty()) armor.add(formatStack(stack));
        }
        List<String> carried = new ArrayList<>();
        int limit = Math.min(inventory.getSlots(), 36);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && !stack.isEmpty()) carried.add(formatStack(stack));
        }
        return ObservedValue.current(new CitizenEquipmentView(armor, carried), gameTime);
    }

    private static String formatStack(ItemStack stack) {
        return stack.getCount() + "x " + stack.getDisplayName().getString();
    }
}
