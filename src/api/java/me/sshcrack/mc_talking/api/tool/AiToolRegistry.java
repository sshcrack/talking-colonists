package me.sshcrack.mc_talking.api.tool;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/**
 * Registry for addon-provided AI tools.
 *
 * <p>Addons register a namespaced ID such as {@code colonist_errands:come_here}. Talking Colonists
 * maps that ID to the provider-facing function name internally. Addons should only persist/use the
 * namespaced ID.</p>
 */
public final class AiToolRegistry {
    private AiToolRegistry() {
    }

    /**
     * Registers one addon tool.
     *
     * @return a close-safe lifetime handle for exactly this registration
     */
    public static @NotNull AddonRegistration register(
            @NotNull String namespace,
            @NotNull String name,
            @NotNull AiTool tool
    ) {
        return TalkingColonistsApi.services().registerAiTool(namespace, name, tool);
    }
}
