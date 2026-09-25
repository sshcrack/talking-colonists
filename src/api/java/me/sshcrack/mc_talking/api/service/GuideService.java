package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.guide.AddonGuide;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Runtime service for addon guides and the Colony Handbook. */
public interface GuideService {
    @NotNull AddonRegistration register(@NotNull AddonGuide guide);

    @NotNull List<AddonGuide> guides();
}
