package me.sshcrack.mc_talking.api.pregen;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Registry for safe addon transforms of pregenerated line directives. */
public final class PregenerationPromptService {
    private PregenerationPromptService() {
    }

    /**
     * Registers a modifier. Modifiers run in ascending {@code order}, then by namespaced ID.
     */
    public static @NotNull AddonRegistration registerModifier(
            @NotNull String id,
            int order,
            @NotNull PregenerationPromptModifier modifier
    ) {
        return TalkingColonistsApi.services().registerPregenerationPromptModifier(id, order, modifier);
    }
}
