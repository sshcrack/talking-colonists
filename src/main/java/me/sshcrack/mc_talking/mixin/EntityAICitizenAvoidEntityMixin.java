package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAICitizenAvoidEntity;
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

@Mixin(value = EntityAICitizenAvoidEntity.class, remap = false)
public class EntityAICitizenAvoidEntityMixin {

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
        method = "isEntityClose",
        at = @At("HEAD")
    )
    private void mc_talking$onIsEntityClose(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.FLEE_CHECKING);
    }

    @Inject(
        method = "performMoveAway",
        at = @At("HEAD")
    )
    private void mc_talking$onPerformMoveAway(CallbackInfoReturnable<Boolean> cir) {
        mc_talking$setSubState(MinimalAISubState.FLEE_RUNNING);
    }

    @Inject(
        method = "updateMoving",
        at = @At("HEAD")
    )
    private void mc_talking$onUpdateMoving(CallbackInfoReturnable<Boolean> cir) {
        mc_talking$setSubState(MinimalAISubState.FLEE_RUNNING);
    }
}
