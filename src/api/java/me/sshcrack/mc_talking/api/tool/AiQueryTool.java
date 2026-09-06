package me.sshcrack.mc_talking.api.tool;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Synchronous, read-oriented AI tool.
 *
 * <p>Talking Colonists invokes queries on the Minecraft server thread after revalidating schema,
 * session scope and permissions. Implementations must return promptly. Use {@link AiCommandTool}
 * for world-changing or delayed work.</p>
 */
public interface AiQueryTool extends AiTool {
    @NotNull JsonObject executeQuery(@NotNull AiToolContext context, @Nullable JsonObject parameters);
}
