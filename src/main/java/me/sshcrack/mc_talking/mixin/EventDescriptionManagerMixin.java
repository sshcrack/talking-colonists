package me.sshcrack.mc_talking.mixin;

import com.minecolonies.core.colony.managers.EventDescriptionManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = EventDescriptionManager.class, remap = false)
public class EventDescriptionManagerMixin {
}
