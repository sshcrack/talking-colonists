package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.RenderBipedCitizen;
import me.sshcrack.mc_talking.client.CitizenMouthLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderBipedCitizen.class, remap = false)
public abstract class CitizenMouthLayerMixin extends MobRenderer<AbstractEntityCitizen, CitizenModel<AbstractEntityCitizen>> {
    protected CitizenMouthLayerMixin(EntityRendererProvider.Context context, CitizenModel<AbstractEntityCitizen> model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mc_talking$addMouth(EntityRendererProvider.Context context, CallbackInfo ci) {
        addLayer(new CitizenMouthLayer(this));
    }
}
