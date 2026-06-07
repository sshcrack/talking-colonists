package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.core.entity.ai.minimal.EntityAISickTask;
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

@Mixin(value = EntityAISickTask.class, remap = false)
public class EntityAISickTaskMixin {

    @Shadow
    @Final
    private EntityCitizen citizen;

    @Unique
    private String mc_talking$getDiseaseName() {
        var data = citizen.getCitizenData();
        if (data == null) return null;
        var handler = data.getCitizenDiseaseHandler();
        if (handler == null) return null;
        var disease = handler.getDisease();
        if (disease == null) return null;
        return disease.name().getString();
    }

    @Unique
    private void mc_talking$setSubState(MinimalAISubState state) {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(state, mc_talking$getDiseaseName());
    }

    @Unique
    private void mc_talking$clearSubState() {
        var data = citizen.getCitizenData();
        if (data == null) return;
        ((CitizenMinimalAISubStateProvider) data).mc_talking$setMinimalAiSubState(null, null);
    }

    @Inject(
        method = "checkForCure",
        at = @At("HEAD")
    )
    private void mc_talking$onCheckForCure(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_CHECKING_FOR_CURE);
    }

    @Inject(method = "checkForCure", at = @At("RETURN"))
    private void mc_talking$onCheckForCureReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "goToHut",
        at = @At("HEAD")
    )
    private void mc_talking$onGoToHut(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_WALKING_TO_HOSPITAL);
    }

    @Inject(method = "goToHut", at = @At("RETURN"))
    private void mc_talking$onGoToHutReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "goToHospital",
        at = @At("HEAD")
    )
    private void mc_talking$onGoToHospital(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_WALKING_TO_HOSPITAL);
    }

    @Inject(method = "goToHospital", at = @At("RETURN"))
    private void mc_talking$onGoToHospitalReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "searchHospital",
        at = @At("HEAD")
    )
    private void mc_talking$onSearchHospital(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_AT_HOSPITAL);
    }

    @Inject(method = "searchHospital", at = @At("RETURN"))
    private void mc_talking$onSearchHospitalReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "waitForCure",
        at = @At("HEAD")
    )
    private void mc_talking$onWaitForCure(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_AT_HOSPITAL);
    }

    @Inject(method = "waitForCure", at = @At("RETURN"))
    private void mc_talking$onWaitForCureReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "findEmptyBed",
        at = @At("HEAD")
    )
    private void mc_talking$onFindEmptyBed(CallbackInfoReturnable<EntityAISickTask.DiseaseState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_AT_HOSPITAL);
    }

    @Inject(method = "findEmptyBed", at = @At("RETURN"))
    private void mc_talking$onFindEmptyBedReturn(CallbackInfoReturnable<EntityAISickTask.DiseaseState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "applyCure",
        at = @At("HEAD")
    )
    private void mc_talking$onApplyCure(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_RECEIVING_CURE);
    }

    @Inject(method = "applyCure", at = @At("RETURN"))
    private void mc_talking$onApplyCureReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }

    @Inject(
        method = "wander",
        at = @At("HEAD")
    )
    private void mc_talking$onWander(CallbackInfoReturnable<IState> cir) {
        mc_talking$setSubState(MinimalAISubState.SICK_WANDERING);
    }

    @Inject(method = "wander", at = @At("RETURN"))
    private void mc_talking$onWanderReturn(CallbackInfoReturnable<IState> cir) {
        mc_talking$clearSubState();
    }
}
