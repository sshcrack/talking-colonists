package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.intro.Introduction;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.IntroductionService;
import me.sshcrack.mc_talking.internal.registration.RegistrationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The registered introductions, with Talking Colonists' own welcome first. The welcome is created on
 * first use by the runner, not when the API backend loads: its trigger lambda resolves Minecraft and
 * MineColonies types, which unit tests of the backend must not load.
 */
public final class IntroductionServiceBackend implements IntroductionService {
    /** A citizen welcomes the player to their colony and hands them the Colony Handbook. */
    public static final String WELCOME_ID = "mc_talking:welcome";

    private static final RegistrationRegistry<Introduction> INTRODUCTIONS = new RegistrationRegistry<>("introduction");
    private static boolean welcomeRegistered;

    @Override
    public @NotNull AddonRegistration register(@NotNull Introduction introduction) {
        return INTRODUCTIONS.register(introduction.id(), 0, introduction);
    }

    /** Every introduction, the welcome first, then sorted by id. */
    public static @NotNull List<Introduction> registered() {
        registerWelcome();
        return INTRODUCTIONS.orderedSnapshot().stream().map(RegistrationRegistry.Entry::value).toList();
    }

    private static synchronized void registerWelcome() {
        if (welcomeRegistered) return;
        welcomeRegistered = true;
        INTRODUCTIONS.register(WELCOME_ID, Integer.MIN_VALUE, new Introduction(WELCOME_ID, "the Colony Handbook",
                "This is their first impression of the colony, so be kind and glad to meet them, whatever your mood today. "
                        + "Welcome them and tell them the handbook explains how things work here.",
                GuideServiceBackend.TALKING.id(), (player, colony) -> true));
    }
}
