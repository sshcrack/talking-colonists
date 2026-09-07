package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.pregen.PregenerationPromptModifier;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime service for pregeneration prompt modifiers. */
public interface PregenerationService {
    @NotNull AddonRegistration registerPromptModifier(@NotNull String id, int order, @NotNull PregenerationPromptModifier modifier);
}
