package me.sshcrack.mc_talking.api.tool;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous command AI tool for world-changing or delayed work.
 *
 * <p>Talking Colonists starts {@link #executeCommand} on the Minecraft server thread and returns an
 * {@code accepted} operation result to the model without waiting for the returned stage. The
 * implementation must therefore return its stage promptly. Later continuations that mutate the
 * world should use {@link AiToolContext#runOnServerThread(Runnable)} or
 * {@link AiToolContext#supplyOnServerThread(java.util.function.Supplier)}.</p>
 */
public interface AiCommandTool extends AiTool {
    @NotNull CompletionStage<JsonObject> executeCommand(
            @NotNull AiToolContext context,
            @Nullable JsonObject parameters
    );

    /**
     * Called exactly once after an accepted command reaches a terminal state.
     *
     * <p>The callback is also invoked when the owning conversation has already ended. Inspect
     * {@link AiToolOperationOutcome#deliveredToSession()} to distinguish that case. The callback
     * may run off the Minecraft server thread.</p>
     */
    default void onCompletion(@NotNull AiToolOperationOutcome outcome) {
    }
}
