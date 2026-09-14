package me.sshcrack.mc_talking.api.tool;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.registration.NamespacedAddonId;
import org.jetbrains.annotations.NotNull;

/**
 * Registry for addon-provided AI tools.
 *
 * <p>Addons register a namespaced ID such as {@code colonist_errands:come_here}. Talking Colonists
 * maps that ID to the provider-facing function name internally. Addons should only persist/use the
 * namespaced ID.</p>
 */
public final class AiToolRegistry {
    private static final int MAX_PROVIDER_NAME_LENGTH = 128;

    private AiToolRegistry() {
    }

    /**
     * Returns the exact provider-facing function name for an addon tool. Use this when a prompt or
     * tool description must refer to the function name Gemini sees; registration still uses the
     * public namespaced addon ID.
     */
    public static @NotNull String providerName(@NotNull String addonId, @NotNull String tool) {
        NamespacedAddonId id = new NamespacedAddonId(addonId, tool);
        String providerName = "tc_" + id.namespace().length() + "_" + id.namespace() + "_" + id.name();
        if (providerName.length() > MAX_PROVIDER_NAME_LENGTH) {
            throw new IllegalArgumentException("Tool provider name is too long after namespacing: " + id);
        }
        return providerName;
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
        return TalkingColonistsApi.services().tools().register(namespace, name, tool);
    }
}
