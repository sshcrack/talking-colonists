package me.sshcrack.mc_talking.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingNetherWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BuildingNetherWorker.class, remap = false)
public interface BuildingNetherWorkerAccessor {
    @Accessor("currentTrips")
    int getCurrentTrips();

    @Accessor("currentPeriodDay")
    int getCurrentPeriodDay();
}
