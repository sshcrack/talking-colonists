package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.colony.CitizenData;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CitizenData.class, remap = false)
public class CitizenDataMixin {
    @Inject(method = "setVisibleStatus", at = @At("HEAD"))
    private void mc_talking$onSetVisibleStatus(VisibleCitizenStatus status, CallbackInfo ci) {
        var data = CitizenData.class.cast(this);
        if (status == null || data == null) {
            return;
        }

        var client = ConversationManager.getClientForEntity(data.getUUID());
        if (client == null) {
            return;
        }

        var statusView = CitizenPromptViewFactory.createStatusView(status, data);
        var newStatusPrompt = String.format("You are now %s", CitizenPromptService.formatStatus(statusView));
        client.promptSystemText(newStatusPrompt);
    }
}
