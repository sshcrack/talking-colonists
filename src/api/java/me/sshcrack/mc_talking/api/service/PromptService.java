package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.prompt.CitizenPromptContributor;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptProvider;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime service for addon prompt registrations. */
public interface PromptService {
    @NotNull AddonRegistration registerProvider(@NotNull String id, int priority, @NotNull CitizenPromptProvider provider);
    @NotNull AddonRegistration registerContributor(@NotNull String id, int order, @NotNull CitizenPromptContributor contributor);
}
