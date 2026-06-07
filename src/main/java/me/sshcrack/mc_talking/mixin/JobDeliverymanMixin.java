package me.sshcrack.mc_talking.mixin;

import com.minecolonies.core.colony.jobs.JobDeliveryman;
import me.sshcrack.mc_talking.duck.CitizenRecentActionsProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JobDeliveryman.class, remap = false)
public class JobDeliverymanMixin {

    @Inject(method = "finishRequest", at = @At("RETURN"))
    private void mc_talking$onFinishRequest(boolean successful, CallbackInfo ci) {
        var job = JobDeliveryman.class.cast(this);
        var citizenData = job.getCitizen();
        if (citizenData == null) return;

        String entry = successful
            ? "Just completed a delivery run for the colony."
            : "A delivery run failed or was cancelled.";

        ((CitizenRecentActionsProvider) citizenData)
            .mc_talking$pushRecentAction(entry);
    }
}
