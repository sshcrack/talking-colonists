package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Immutable policy for one addon-controlled conversation session. */
public record ControlledConversationOptions(
        boolean allowAllAddonTools,
        @NotNull Set<String> allowedAddonTools
) {
    public ControlledConversationOptions {
        allowedAddonTools = Set.copyOf(allowedAddonTools);
        if (allowAllAddonTools && !allowedAddonTools.isEmpty()) {
            throw new IllegalArgumentException("allowAllAddonTools and an explicit allow-list are mutually exclusive");
        }
    }

    /** No addon tools are advertised or executable. Built-in core tools keep their normal policy. */
    public static @NotNull ControlledConversationOptions noAddonTools() {
        return new ControlledConversationOptions(false, Set.of());
    }

    /** Only the supplied namespaced addon tool IDs are available to controlled turns. */
    public static @NotNull ControlledConversationOptions allowAddonTools(@NotNull Set<String> toolIds) {
        return new ControlledConversationOptions(false, toolIds);
    }

    /** All otherwise-enabled addon tools are available to controlled turns. */
    public static @NotNull ControlledConversationOptions allAddonTools() {
        return new ControlledConversationOptions(true, Set.of());
    }

    public boolean allowsAddonTool(@NotNull String toolId) {
        return allowAllAddonTools || allowedAddonTools.contains(toolId);
    }
}
