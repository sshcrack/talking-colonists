package me.sshcrack.mc_talking.api.intro;

import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/**
 * Introductions ({@link ApiFeature#INTRODUCTIONS}): citizens tell each player about addon features once,
 * when they become relevant. Register them in the mod constructor; they only run on the server.
 */
public final class CitizenIntroductionService {
    private CitizenIntroductionService() {
    }

    /**
     * Registers an introduction. Registering an id that is already registered fails; close the returned
     * registration to remove it. Players who already heard it are remembered by id.
     */
    public static @NotNull AddonRegistration register(@NotNull Introduction introduction) {
        TalkingColonistsApi.requireSupported(ApiFeature.INTRODUCTIONS);
        return TalkingColonistsApi.services().introductions().register(introduction);
    }
}
