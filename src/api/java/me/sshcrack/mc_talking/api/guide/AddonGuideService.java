package me.sshcrack.mc_talking.api.guide;

import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Guides to addon features ({@link ApiFeature#ADDON_GUIDES}): the chapters of the Colony Handbook, which
 * citizens also know. Register them in the mod constructor, on both the client (the handbook window
 * reads them) and the server (citizens' prompts read them).
 */
public final class AddonGuideService {
    private AddonGuideService() {
    }

    /**
     * Registers a guide. Registering an id that is already registered fails; close the returned
     * registration to remove the guide.
     */
    public static @NotNull AddonRegistration register(@NotNull AddonGuide guide) {
        TalkingColonistsApi.requireSupported(ApiFeature.ADDON_GUIDES);
        return TalkingColonistsApi.services().guides().register(guide);
    }

    /** Every registered guide, sorted by id; Talking Colonists' own guide comes first. */
    public static @NotNull List<AddonGuide> guides() {
        TalkingColonistsApi.requireSupported(ApiFeature.ADDON_GUIDES);
        return TalkingColonistsApi.services().guides().guides();
    }
}
