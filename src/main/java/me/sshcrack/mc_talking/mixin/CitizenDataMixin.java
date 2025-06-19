package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.CitizenData;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.manager.CitizenContextUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CitizenData.class, remap = false)
public class CitizenDataMixin {
    @Inject(method = "setVisibleStatus", at = @At("HEAD"))
    private void mc_talking$onSetVisibleStatus(VisibleCitizenStatus status, CallbackInfo ci) {
        if (status == null)
            return;

        var data = CitizenData.class.cast(this);
        var client = ConversationManager.getClientForEntity(data.getUUID());
        if (client == null)
            return;

        var newStatusPrompt = String.format("You are now %s", CitizenContextUtils.formatStatus(status, data));
        client.promptSystemText(newStatusPrompt);
    }
}
