package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptContributor;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.PromptService;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import org.jetbrains.annotations.NotNull;

final class PromptServiceBackend implements PromptService {
    @Override
    public @NotNull AddonRegistration registerProvider(@NotNull String id, int priority,
                                                        @NotNull CitizenPromptProvider provider) {
        return PromptRuntime.registerProvider(id, priority, provider);
    }

    @Override
    public @NotNull AddonRegistration registerContributor(@NotNull String id, int order,
                                                           @NotNull CitizenPromptContributor contributor) {
        return PromptRuntime.registerContributor(id, order, contributor);
    }
}
