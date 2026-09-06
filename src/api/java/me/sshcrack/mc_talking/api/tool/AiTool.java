package me.sshcrack.mc_talking.api.tool;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supported addon-facing Gemini tool contract.
 *
 * <p>Implementations should be fast and thread-safe. Gemini callbacks are not guaranteed to run
 * on the Minecraft server thread; world mutations must be scheduled onto the server thread by the
 * addon. A future command API may provide managed asynchronous execution for longer operations.</p>
 */
public interface AiTool {
    /** Human-readable description shown to the model. */
    @NotNull String description();

    /** Optional provider-neutral parameter schema. */
    default @Nullable AiToolParameter parameters() {
        return null;
    }

    /** Session scope for this tool. */
    default @NotNull AiToolScope scope() {
        return AiToolScope.ANY_SESSION;
    }

    /** Whether this tool should currently be advertised at all. */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Runtime permission hook. This is evaluated again for every call using authoritative context.
     */
    default boolean canExecute(@NotNull AiToolContext context) {
        return true;
    }

    /** Executes the tool and returns the structured result sent back to Gemini. */
    @NotNull JsonObject execute(@NotNull AiToolContext context, @Nullable JsonObject parameters);
}
