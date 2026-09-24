package me.sshcrack.mc_talking.broadcast;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@link BroadcastPublisher.Colony} backed by a live MineColonies colony. Use on the server thread. */
public final class MineColoniesBroadcastColony implements BroadcastPublisher.Colony {
    private final IColony colony;
    private final double nearRange;

    public MineColoniesBroadcastColony(@NotNull IColony colony, double nearRange) {
        this.colony = colony;
        this.nearRange = nearRange;
    }

    @Override
    public @NotNull Object rateKey() {
        return colony.getDimension().location() + "#" + colony.getID();
    }

    @Override
    public @NotNull List<Citizen> all() {
        List<Citizen> citizens = new ArrayList<>();
        for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
            Citizen citizen = Citizen.of(data);
            if (citizen != null) citizens.add(citizen);
        }
        return citizens;
    }

    @Override
    public @Nullable Citizen byId(@NotNull UUID citizenId) {
        for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
            if (citizenId.equals(data.getUUID())) return Citizen.of(data);
        }
        return null;
    }

    @Override
    public @NotNull List<Citizen> near(@NotNull BlockPos position) {
        double rangeSqr = nearRange * nearRange;
        List<Citizen> citizens = new ArrayList<>();
        for (ICitizenData data : colony.getCitizenManager().getCitizens()) {
            var entity = data.getEntity();
            if (entity.isEmpty() || !entity.get().isAlive()) continue;
            if (entity.get().distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > rangeSqr) {
                continue;
            }
            Citizen citizen = Citizen.of(data);
            if (citizen != null) citizens.add(citizen);
        }
        return citizens;
    }

    record Citizen(@NotNull UUID citizenId, @NotNull String name, @NotNull CitizenMemories memories)
            implements BroadcastPublisher.Recipient {
        static @Nullable Citizen of(ICitizenData data) {
            if (!(data instanceof CitizenDataMemoryExtended extended)) return null;
            return new Citizen(data.getUUID(), data.getName(), extended.mc_talking$getOrInitializeMemory());
        }
    }
}
