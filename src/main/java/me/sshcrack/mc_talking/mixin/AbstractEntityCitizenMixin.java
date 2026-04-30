package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.network.AiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = AbstractEntityCitizen.class, remap = false)
public class AbstractEntityCitizenMixin implements AbstractEntityCitizenAiStatusProvider {
    @Unique
    private AiStatus mc_talking$status = AiStatus.NONE;
    @Unique
    private boolean mc_talking$isStatusDirty = false;

    @Override
    public AiStatus mc_talking$getAiStatus() {
        return mc_talking$status;
    }

    @Override
    public void mc_talking$setStatus(AiStatus status) {
        this.mc_talking$status = status;
        mc_talking$isStatusDirty = true;
    }

    @Override
    public boolean mc_talking$isStatusDirty() {
        return mc_talking$isStatusDirty;
    }

    @Override
    public void mc_talking$markStatusClean() {
        this.mc_talking$isStatusDirty = false;
    }
}
