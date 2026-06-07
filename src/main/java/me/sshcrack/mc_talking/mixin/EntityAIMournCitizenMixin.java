package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAIMournCitizen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import me.sshcrack.mc_talking.duck.CitizenMinimalAISubStateProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityAIMournCitizen.class, remap = false)
public class EntityAIMournCitizenMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Unique
    private String mc_talking$getDeceasedName() {
        var data = citizen.getCitizenData();
        if (data == null) return null;
        var mournHandler = data.getCitizenMournHandler();
        if (mournHandler == null) return null;
        var deceased = mournHandler.getDeceasedCitizens();
        if (deceased == null || deceased.isEmpty()) return null;
        return deceased.iterator().next();
    }

    @Unique
    private void mc_talking$setSubState(MinimalAISubState state) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(state, mc_talking$getDeceasedName());
    }

    @Inject(
        method = "walkToTownHall",
        at = @At("HEAD")
    )
    private void mc_talking$onWalkToTownHall(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_AT_TOWNHALL);
    }

    @Inject(
        method = "walkToGraveyard",
        at = @At("HEAD")
    )
    private void mc_talking$onWalkToGraveyard(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_WALKING_TO_GRAVEYARD);
    }

    @Inject(
        method = "wanderAtGraveyard",
        at = @At("HEAD")
    )
    private void mc_talking$onWanderAtGraveyard(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_AT_GRAVE);
    }

    @Inject(
        method = "walkToGrave",
        at = @At("HEAD")
    )
    private void mc_talking$onWalkToGrave(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_AT_GRAVE);
    }

    @Inject(
        method = "wander",
        at = @At("HEAD")
    )
    private void mc_talking$onWander(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_WALKING);
    }

    @Inject(
        method = "stare",
        at = @At("HEAD")
    )
    private void mc_talking$onStare(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_STARING);
    }

    @Inject(
        method = "decide",
        at = @At("HEAD")
    )
    private void mc_talking$onDecide(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.MOURN_WALKING);
    }
}
