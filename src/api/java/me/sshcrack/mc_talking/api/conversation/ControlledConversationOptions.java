package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable policy for one addon-controlled conversation session. */
public record ControlledConversationOptions(
        boolean allowAllAddonTools,
        @NotNull Set<String> allowedAddonTools
) {
    private static final Pattern TOOL_ID = Pattern.compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9_]{0,31}");

    public ControlledConversationOptions {
        allowedAddonTools = Set.copyOf(Objects.requireNonNull(allowedAddonTools, "allowedAddonTools"));
        if (allowAllAddonTools && !allowedAddonTools.isEmpty()) {
            throw new IllegalArgumentException("allowAllAddonTools and an explicit allow-list are mutually exclusive");
        }
        for (String toolId : allowedAddonTools) {
            if (!TOOL_ID.matcher(toolId).matches()) {
                throw new IllegalArgumentException("controlled-session addon tool ID must match " + TOOL_ID.pattern());
            }
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
