package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAISleep;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import me.sshcrack.mc_talking.duck.CitizenMinimalAISubStateProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityAISleep.class, remap = false)
public class EntityAISleepMixin {

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
        method = "checkSleep",
        at = @At("HEAD")
    )
    private void mc_talking$onCheckSleep(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_WALKING_TO_BED);
    }

    @Inject(
        method = "walkHome",
        at = @At("HEAD")
    )
    private void mc_talking$onWalkHome(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_WALKING_TO_BED);
    }

    @Inject(
        method = "findBedAndTryToSleep",
        at = @At("HEAD")
    )
    private void mc_talking$onFindBed(CallbackInfo ci) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_FINDING_BED);
    }

    @Inject(
        method = "sleep",
        at = @At("HEAD")
    )
    private void mc_talking$onSleep(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_IN_BED);
    }
}
