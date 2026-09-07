package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.pregen.PregenerationPromptModifier;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.PregenerationService;
import org.jetbrains.annotations.NotNull;

final class PregenerationServiceBackend implements PregenerationService {
    @Override
    public @NotNull AddonRegistration registerPromptModifier(@NotNull String id, int order,
                                                              @NotNull PregenerationPromptModifier modifier) {
        return PregenerationPromptRuntime.register(id, order, modifier);
    }
}
