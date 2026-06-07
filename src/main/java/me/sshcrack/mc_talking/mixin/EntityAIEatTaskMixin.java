package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAIEatTask;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import me.sshcrack.mc_talking.duck.CitizenMinimalAISubStateProvider;
import me.sshcrack.mc_talking.duck.CitizenRecentActionsProvider;
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
    private void mc_talking$setSubState(MinimalAISubState state) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(state);
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
        ((CitizenRecentActionsProvider) data)
            .mc_talking$pushRecentAction("Finished eating and returned to full saturation.");
    }

    @Inject(
        method = "searchRestaurant",
        at = @At("HEAD")
    )
    private void mc_talking$onSearchRestaurant(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_SEARCH_RESTAURANT);
    }

    @Inject(
        method = "searchRestaurant",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/api/colony/ICitizenData;triggerInteraction(Lcom/minecolonies/api/colony/interactionhandling/IInteractionResponseHandler;)V"
        )
    )
    private void mc_talking$onNoRestaurant(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenRecentActionsProvider) data)
            .mc_talking$pushRecentAction(
                "Tried to find somewhere to eat but the colony has no restaurant.");
    }

    @Inject(
        method = "getFood",
        at = @At("HEAD")
    )
    private void mc_talking$onGetFood(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(
        method = "goToRestaurant",
        at = @At("HEAD")
    )
    private void mc_talking$onGoToRestaurant(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(
        method = "waitForFood",
        at = @At("HEAD")
    )
    private void mc_talking$onWaitForFood(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_WAITING_FOOD);
    }

    @Inject(
        method = "getFoodYourself",
        at = @At("HEAD")
    )
    private void mc_talking$onGetFoodYourself(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_GET_FOOD);
    }

    @Inject(
        method = "goToEatingPlace",
        at = @At("HEAD")
    )
    private void mc_talking$onGoToEatingPlace(CallbackInfoReturnable<EntityAIEatTask.EatingState> cir) {
        mc_talking$setSubState(MinimalAISubState.EAT_EATING);
    }
}
