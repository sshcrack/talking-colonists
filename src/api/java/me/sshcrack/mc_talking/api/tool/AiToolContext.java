package me.sshcrack.mc_talking.api.tool;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Authoritative execution context supplied by Talking Colonists when the provider calls an addon tool.
 *
 * <p>The player is resolved from the owning conversation by core. Addons must not accept a player
 * UUID, rank, or other authority-bearing identity from model-supplied JSON parameters.</p>
 */
public record AiToolContext(
        @NotNull AbstractEntityCitizen citizen,
        @NotNull IColony colony,
        @Nullable ServerPlayer player
) {
    public boolean hasPlayer() {
        return player != null;
    }

    /**
     * Returns the authenticated initiating player or throws when the tool was called outside a
     * direct player conversation. Prefer {@link AiToolScope#PLAYER_CONVERSATION} for such tools.
     */
    public @NotNull ServerPlayer requirePlayer() {
        if (player == null) {
            throw new IllegalStateException("This tool call has no authenticated player");
        }
        return player;
    }

    /** Returns the authoritative Minecraft server that owns this citizen. */
    public @NotNull MinecraftServer server() {
        MinecraftServer server = citizen.level().getServer();
        if (server == null) throw new IllegalStateException("Citizen is not attached to a server");
        return server;
    }

    /**
     * Schedules a world mutation on the Minecraft server thread. If already on that thread, the
     * action executes immediately. The returned future completes on the server thread.
     */
    public @NotNull CompletableFuture<Void> runOnServerThread(@NotNull Runnable action) {
        return supplyOnServerThread(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs/schedules a value-producing operation on the Minecraft server thread without exposing
     * Talking Colonists executors. Exceptions complete the returned future exceptionally.
     */
    public <T> @NotNull CompletableFuture<T> supplyOnServerThread(@NotNull Supplier<T> action) {
        MinecraftServer server = server();
        CompletableFuture<T> future = new CompletableFuture<>();
        Runnable invoke = () -> {
            try {
                future.complete(action.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        if (server.isSameThread()) invoke.run();
        else server.execute(invoke);
        return future;
    }
}
