package me.sshcrack.mc_talking.api.prompt;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Public registration surface for prompt extensions. */
public final class CitizenPromptService {
    private CitizenPromptService() {
    }

    /**
     * Registers a complete provider override.
     *
     * <p>Most addons should use contributors instead. If several provider overrides are installed,
     * the highest {@code priority} wins; ties are resolved deterministically by namespaced ID.</p>
     */
    public static @NotNull AddonRegistration registerProvider(
            @NotNull String id,
            int priority,
            @NotNull CitizenPromptProvider provider
    ) {
        return TalkingColonistsApi.services().registerPromptProvider(id, priority, provider);
    }

    /**
     * Registers composable addon context without replacing the active prompt provider.
     * Contributors run in ascending {@code order}, then lexicographic namespaced ID order.
     */
    public static @NotNull AddonRegistration registerContributor(
            @NotNull String id,
            int order,
            @NotNull CitizenPromptContributor contributor
    ) {
        return TalkingColonistsApi.services().registerPromptContributor(id, order, contributor);
    }
}
