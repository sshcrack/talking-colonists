package me.sshcrack.mc_talking.mixin;

import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractEntityAIBasic.class, remap = false)
public interface AbstractEntityAIInteractAccessor {
    @Accessor("building")
    AbstractBuilding getBuilding();
}
