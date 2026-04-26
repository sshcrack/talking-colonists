package me.sshcrack.mc_talking.mixin;

import com.minecolonies.api.colony.colonyEvents.descriptions.IColonyEventDescription;
import com.minecolonies.core.colony.managers.EventDescriptionManager;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EventDescriptionManager.class, remap = false)
public class EventDescriptionManagerMixin {
    @Inject(method="addEventDescription", at=@At("HEAD"))
    private void mc_talking$onEvent(IColonyEventDescription event, CallbackInfo ci) {

    }
}
