package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import me.sshcrack.mc_talking.duck.CitizenMinimalAISubStateProvider;
import me.sshcrack.mc_talking.duck.CitizenRecentActionsProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityAIEatTask.class, remap = false)
public class EntityAIEatTaskMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Unique
    private String mc_talking$getRestaurantContext() {
        var data = citizen.getCitizenData();
        if (data == null) return null;
        IColony colony = data.getColony();
        if (colony == null) return null;
        var bm = colony.getServerBuildingManager();
        if (bm == null) return null;
        var origin = citizen.blockPosition();
        BlockPos best = bm.getBestBuilding(origin, BuildingCook.class);
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

    @Unique
    private void mc_talking$setSubState(MinimalAISubState state) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(state, mc_talking$getRestaurantContext());
    }

    @Unique
    private void mc_talking$clearSubState() {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(null, null);
    }

    @Inject(
        method = "eat",
        at = @At("HEAD")
    )
    private void mc_talking$onEat(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_EATING);
    }

    @Inject(
        method = "eat",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/api/colony/ICitizenData;setJustAte(Z)V"
        )
    )
    private void mc_talking$onEatComplete(CallbackInfoReturnable<IState> cir) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenRecentActionsProvider) data).mc_talking$pushRecentAction("Finished eating and returned to full saturation.");
    }

    @Inject(method = "eat", at = @At("RETURN"))
    private void mc_talking$onEatReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "searchRestaurant",
        at = @At("HEAD")
    )
    private void mc_talking$onSearchRestaurant(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_SEARCH_RESTAURANT);
    }

    @Inject(method = "searchRestaurant", at = @At("RETURN"))
    private void mc_talking$onSearchRestaurantReturn(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "getFood",
        at = @At("HEAD")
    )
    private void mc_talking$onGetFood(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(method = "getFood", at = @At("RETURN"))
    private void mc_talking$onGetFoodReturn(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "goToRestaurant",
        at = @At("HEAD")
    )
    private void mc_talking$onGoToRestaurant(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(method = "goToRestaurant", at = @At("RETURN"))
    private void mc_talking$onGoToRestaurantReturn(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "waitForFood",
        at = @At("HEAD")
    )
    private void mc_talking$onWaitForFood(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_WAITING_FOOD);
    }

    @Inject(method = "waitForFood", at = @At("RETURN"))
    private void mc_talking$onWaitForFoodReturn(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "getFoodYourself",
        at = @At("HEAD")
    )
    private void mc_talking$onGetFoodYourself(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(method = "getFoodYourself", at = @At("RETURN"))
    private void mc_talking$onGetFoodYourselfReturn(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "goToEatingPlace",
        at = @At("HEAD")
    )
    private void mc_talking$onGoToEatingPlace(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(method = "goToEatingPlace", at = @At("RETURN"))
    private void mc_talking$onGoToEatingPlaceReturn(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$clearSubState();
    }
}
