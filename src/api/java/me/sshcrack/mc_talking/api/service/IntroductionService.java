package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.intro.Introduction;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime service for citizens' introductions of addon features. */
public interface IntroductionService {
    @NotNull AddonRegistration register(@NotNull Introduction introduction);
}
