package me.sshcrack.mc_talking.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingLibrary;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = BuildingLibrary.class, remap = false)
public interface BuildingLibraryAccessor {
    @Accessor("bookCases")
    List<BlockPos> getBookCases();
}
