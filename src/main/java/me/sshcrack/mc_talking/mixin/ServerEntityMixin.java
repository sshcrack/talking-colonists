package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public class ServerEntityMixin {
    @Shadow
    private int tickCount;

    @Shadow
    @Final
    private int updateInterval;

    @Shadow
    @Final
    private Entity entity;


    /*? if neoforge {*/
    @Unique
    private static void mc_talking$sendToPlayersTrackingEntity(LivingEntity entity, AiStatus packet) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity, new AiStatusPayload(entity.getUUID(), packet));
    }
    /*?} */
    /*? if forge {*/
    /*@Unique
    private static void mc_talking$sendToPlayersTrackingEntity(LivingEntity entity, AiStatus packet) {
        AiStatusPayload.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> entity), new AiStatusPayload(entity.getUUID(), packet));
    }*/
    /*?}*/

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void mc_talking$sendAiStatusUpdates(CallbackInfo ci) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return;
        }

        if (this.tickCount % this.updateInterval != 0) {
            return;
        }

        var provider = (AbstractEntityCitizenAiStatusProvider) citizen;
        if (!provider.mc_talking$isStatusDirty()) {
            return;
        }

        McTalking.LOGGER.info("Citizen {} has dirty AI status {}, sending update to clients", citizen.getCitizenData().getName(), provider.mc_talking$getAiStatus());
        mc_talking$sendToPlayersTrackingEntity(citizen, provider.mc_talking$getAiStatus());
        provider.mc_talking$markStatusClean();
    }
}
