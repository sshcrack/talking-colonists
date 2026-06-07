package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAISleep;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.MinimalAISubState;
import me.sshcrack.mc_talking.duck.CitizenMinimalAISubStateProvider;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
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
    private String mc_talking$getHomeName() {
        var data = citizen.getCitizenData();
        if (data == null) return null;
        var home = data.getHomeBuilding();
        if (home == null) return null;
        String displayName = home.getBuildingDisplayName();
        if (displayName != null && !displayName.isEmpty() && !displayName.contains(".") && !displayName.contains("/")) {
            return displayName;
        }
        return Component.translatable(home.getBuildingType().getTranslationKey()).getString();
    }

    @Unique
    private void mc_talking$setSubState(MinimalAISubState state, @Nullable String context) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(state, context);
    }

    @Inject(
        method = "checkSleep",
        at = @At("HEAD")
    )
    private void mc_talking$onCheckSleep(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_WALKING_TO_BED, mc_talking$getHomeName());
    }

    @Inject(
        method = "walkHome",
        at = @At("HEAD")
    )
    private void mc_talking$onWalkHome(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_WALKING_TO_BED, mc_talking$getHomeName());
    }

    @Inject(
        method = "findBedAndTryToSleep",
        at = @At("HEAD")
    )
    private void mc_talking$onFindBed(CallbackInfo ci) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_FINDING_BED, "no spare bed in colony");
    }

    @Inject(
        method = "sleep",
        at = @At("HEAD")
    )
    private void mc_talking$onSleep(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SLEEP_IN_BED, null);
    }
}
