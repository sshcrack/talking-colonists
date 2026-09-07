package me.sshcrack.mc_talking.api.prompt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable context belonging to one conversation/session rather than the global addon registry. */
public record PromptSessionContext(
        @Nullable UUID sessionId,
        @Nullable UUID turnId,
        @Nullable String agenda,
        boolean allowAllAddonTools,
        @NotNull Set<String> allowedAddonTools
) {
    private static final PromptSessionContext EMPTY = new PromptSessionContext(null, null, null, true, Set.of());

    public PromptSessionContext {
        allowedAddonTools = Set.copyOf(Objects.requireNonNull(allowedAddonTools, "allowedAddonTools"));
        if (allowAllAddonTools && !allowedAddonTools.isEmpty()) {
            throw new IllegalArgumentException("allowAllAddonTools and explicit allowedAddonTools are mutually exclusive");
        }
    }

    public static @NotNull PromptSessionContext empty() { return EMPTY; }

    public static @NotNull PromptSessionContext withAgenda(@NotNull String agenda) {
        return new PromptSessionContext(null, null, Objects.requireNonNull(agenda, "agenda"), true, Set.of());
    }

    public static @NotNull PromptSessionContext controlled(
            @NotNull UUID sessionId,
            @NotNull UUID turnId,
            @NotNull String agenda,
            boolean allowAllAddonTools,
            @NotNull Set<String> allowedAddonTools
    ) {
        return new PromptSessionContext(sessionId, turnId, Objects.requireNonNull(agenda, "agenda"),
                allowAllAddonTools, allowedAddonTools);
    }

    public boolean allowsAddonTool(@NotNull String id) {
        return allowAllAddonTools || allowedAddonTools.contains(id);
    }

    public boolean isEmpty() {
        return sessionId == null && turnId == null && agenda == null && allowAllAddonTools && allowedAddonTools.isEmpty();
    }
}
