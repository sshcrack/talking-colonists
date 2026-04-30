package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.util.AiStatusHelper;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.sshcrack.mc_talking.util.AiStatusHelper.DATA_AI_STATUS;

@Mixin(value = AbstractEntityCitizen.class, remap = false)
public class AbstractEntityCitizenMixin {

    @Inject(method="<clinit>", at=@At("HEAD"))
    private static void mc_talking$onStaticInit(CallbackInfo ci) {
        AiStatusHelper.register();
    }

    /*? if neoforge {*/
    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    private void mc_talking$onDefineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(DATA_AI_STATUS, AiStatus.NONE);
    }
    /*?}*/
    /*? if forge {*/
    /*
    @Inject(method = "defineSynchedData", at = @At("HEAD"))
    private void mc_talking$onDefineSynchedData(CallbackInfo ci) {
        var data = AbstractEntityCitizen.class.cast(this);
        data.getEntityData().define(DATA_AI_STATUS, AiStatus.NONE);
    }
     */
    /*?}*/
}
