package me.sshcrack.mc_talking.api.prompt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Immutable context that belongs to one conversation/session rather than the global addon registry.
 *
 * <p>The agenda is intentionally first-class for controlled conversations. This value is a snapshot:
 * changes to a controlled-session agenda affect subsequent turns, not a prompt already being assembled.
 * Additional session concepts should be added here as typed fields instead of string-keyed metadata.</p>
 */
public record PromptSessionContext(@Nullable String agenda) {
    private static final PromptSessionContext EMPTY = new PromptSessionContext(null);

    public static @NotNull PromptSessionContext empty() {
        return EMPTY;
    }

    public static @NotNull PromptSessionContext withAgenda(@NotNull String agenda) {
        return new PromptSessionContext(Objects.requireNonNull(agenda, "agenda"));
    }

    public boolean isEmpty() {
        return agenda == null;
    }
}
