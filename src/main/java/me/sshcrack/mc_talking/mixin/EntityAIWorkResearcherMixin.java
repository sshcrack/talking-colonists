package me.sshcrack.mc_talking.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.core.entity.ai.workers.education.EntityAIWorkResearcher;
import me.sshcrack.mc_talking.duck.CitizenRecentActionsProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.minecolonies.api.entity.ai.statemachine.states.IAIState;

@Mixin(value = EntityAIWorkResearcher.class, remap = false)
public class EntityAIWorkResearcherMixin {

    @Inject(
        method = "study",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/core/colony/buildings/workerbuildings/BuildingUniversity;onSuccess(Lcom/minecolonies/api/research/ILocalResearch;)V"
        )
    )
    private void mc_talking$onResearchSuccess(CallbackInfoReturnable<IAIState> cir, @Local(name = "research") ILocalResearch research) {
        if (research == null) return;

        var accessor = (AbstractAISkeletonAccessor) this;
        var worker = accessor.getWorker();
        var citizenData = worker.getCitizenData();
        if (citizenData == null) return;

        String researchPath = research.getId().getPath();
        String readableName = mc_talking$prettifyResearchPath(researchPath);
        String entry = "Just completed research on " + readableName + ".";

        ((CitizenRecentActionsProvider) citizenData)
            .mc_talking$pushRecentAction(entry);
    }

    @Unique
    private static String mc_talking$prettifyResearchPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        name = name.replace('_', ' ');
        if (name.isEmpty()) return "a new technology";

        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == ' ') {
                capitalize = true;
                result.append(c);
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
