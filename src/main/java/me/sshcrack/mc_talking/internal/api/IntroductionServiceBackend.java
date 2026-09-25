package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.intro.Introduction;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.IntroductionService;
import me.sshcrack.mc_talking.internal.registration.RegistrationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** The registered introductions, with Talking Colonists' own welcome first. */
public final class IntroductionServiceBackend implements IntroductionService {
    /** A citizen welcomes the player to their colony and hands them the Colony Handbook. */
    public static final Introduction WELCOME = new Introduction("mc_talking:welcome", "the Colony Handbook",
            "This is their first impression of the colony, so be kind and glad to meet them, whatever your mood today. "
                    + "Welcome them and tell them the handbook explains how things work here.",
            GuideServiceBackend.TALKING.id(), (player, colony) -> true);

    private static final RegistrationRegistry<Introduction> INTRODUCTIONS = new RegistrationRegistry<>("introduction");

    static {
        INTRODUCTIONS.register(WELCOME.id(), Integer.MIN_VALUE, WELCOME);
    }

    @Override
    public @NotNull AddonRegistration register(@NotNull Introduction introduction) {
        return INTRODUCTIONS.register(introduction.id(), 0, introduction);
    }

    /** Every introduction, the welcome first, then sorted by id. */
    public static @NotNull List<Introduction> registered() {
        return INTRODUCTIONS.orderedSnapshot().stream().map(RegistrationRegistry.Entry::value).toList();
    }
}
