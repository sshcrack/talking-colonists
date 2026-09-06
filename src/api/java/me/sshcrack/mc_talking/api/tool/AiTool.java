package me.sshcrack.mc_talking.api.tool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common metadata and authorization contract for addon-facing AI tools.
 *
 * <p>Implement exactly one of {@link AiQueryTool} or {@link AiCommandTool}. Queries are synchronous
 * and commands are asynchronous. Talking Colonists owns dispatch, schema validation, permission
 * checks, idempotency, server-thread command start, and operation result routing.</p>
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

    /**
     * Core-enforced MineColonies permission required from the authenticated initiating player.
     *
     * <p>{@link AiToolPermission#NONE} is appropriate for read-only/public tools. Any other value
     * implicitly requires an authenticated player and is resolved from current server-side colony
     * permissions at execution time.</p>
     */
    default @NotNull AiToolPermission permission() {
        return AiToolPermission.NONE;
    }

    /** Whether this tool should currently be advertised at all. */
    default boolean isEnabled() {
        return true;
    }

    /**
     * Runtime authorization hook. This is evaluated again for every call using authoritative
     * context after the core permission check. It must be side-effect free and return promptly.
     */
    default boolean canExecute(@NotNull AiToolContext context) {
        return true;
    }
}
