package me.sshcrack.mc_talking.internal.api;

import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.api.colony.AddonColonyEvent;
import me.sshcrack.mc_talking.api.colony.ColonyEventListener;
import me.sshcrack.mc_talking.api.colony.ColonyEventView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.ColonyEventFeedService;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ColonyEventFeedServiceBackend implements ColonyEventFeedService {
    @Override
    public @NotNull List<ColonyEventView> recent(@NotNull IColony colony, @NotNull Duration maxAge) {
        Objects.requireNonNull(colony, "colony");
        Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isNegative()) throw new IllegalArgumentException("maxAge must not be negative");
        int seconds = (int) Math.min(Integer.MAX_VALUE / 20, maxAge.toSeconds());
        return onServerThread(colony, List.of(), () -> ColonyEventBuffer.getRecentEvents(colony, seconds).stream()
                .map(ColonyEventBuffer.ColonyEvent::toView)
                .toList());
    }

    @Override
    public boolean record(@NotNull IColony colony, @NotNull AddonColonyEvent event) {
        Objects.requireNonNull(colony, "colony");
        Objects.requireNonNull(event, "event");
        return onServerThread(colony, false, () -> {
            ColonyEventBuffer.recordAddonEvent(colony, event);
            return true;
        });
    }

    @Override
    public @NotNull AddonRegistration registerListener(@NotNull String id, int order, @NotNull ColonyEventListener listener) {
        return ColonyEventBuffer.registerListener(id, order, listener);
    }

    private static <T> T onServerThread(IColony colony, T unavailable, Supplier<T> operation) {
        var level = colony.getWorld();
        MinecraftServer server = level == null ? null : level.getServer();
        if (server == null) return unavailable;
        if (server.isSameThread()) return operation.get();
        return CompletableFuture.supplyAsync(operation, server).join();
    }
}
