package me.sshcrack.mc_talking.api.tool;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Authoritative execution context supplied by Talking Colonists when the provider calls an addon tool.
 *
 * <p>The player and session identity are resolved by core. Addons must not accept a player UUID,
 * rank, session ID, or other authority-bearing identity from model-supplied JSON parameters.</p>
 */
public interface AiToolContext {
    /** Stable identity of the owning Talking Colonists conversation for this connection. */
    @NotNull UUID sessionId();

    /** Citizen whose conversation owns this tool call. */
    @NotNull AbstractEntityCitizen citizen();

    /** Authoritative colony containing {@link #citizen()}. */
    @NotNull IColony colony();

    /** Authenticated initiating player, or {@code null} for NPC/system-only sessions. */
    @Nullable ServerPlayer player();

    default boolean hasPlayer() {
        return player() != null;
    }

    /** Stable authenticated player identity that cannot be supplied by model parameters. */
    default @Nullable UUID authenticatedPlayerId() {
        ServerPlayer player = player();
        return player == null ? null : player.getUUID();
    }

    /**
     * Returns the authenticated initiating player or throws when the tool was called outside a
     * direct player conversation. Prefer {@link AiToolScope#PLAYER_CONVERSATION} for such tools.
     */
    default @NotNull ServerPlayer requirePlayer() {
        ServerPlayer player = player();
        if (player == null) {
            throw new IllegalStateException("This tool call has no authenticated player");
        }
        return player;
    }

    /** Returns the authoritative Minecraft server that owns this citizen. */
    default @NotNull MinecraftServer server() {
        MinecraftServer server = citizen().level().getServer();
        if (server == null) throw new IllegalStateException("Citizen is not attached to a server");
        return server;
    }

    /**
     * Schedules a world mutation on the Minecraft server thread. If already on that thread, the
     * action executes immediately. The returned future completes on the server thread.
     */
    default @NotNull CompletableFuture<Void> runOnServerThread(@NotNull Runnable action) {
        return supplyOnServerThread(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs/schedules a value-producing operation on the Minecraft server thread without exposing
     * Talking Colonists executors. Exceptions complete the returned future exceptionally.
     */
    default <T> @NotNull CompletableFuture<T> supplyOnServerThread(@NotNull Supplier<T> action) {
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
