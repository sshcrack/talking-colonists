package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.guide.AddonGuide;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.GuideService;
import me.sshcrack.mc_talking.internal.registration.RegistrationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** The registered addon guides, with Talking Colonists' own guide as the first chapter. */
public final class GuideServiceBackend implements GuideService {
    /** The handbook's first chapter: how to talk to citizens at all. */
    public static final AddonGuide TALKING = new AddonGuide("mc_talking:talking", "Talking to your citizens",
            "Your citizens have their own voices: they answer aloud, remember what you tell them, and pass news on to "
                    + "each other. Unhappy citizens may come to you with their worries.",
            List.of(
                    "Craft a Citizen Communication Device from a book and quill and a redstone torch.",
                    "Left-click a citizen with it, then just speak through Simple Voice Chat; they answer aloud.",
                    "Ask them about their work, their home, the colony, or how something here works.",
                    "No microphone? Start a chat line with @ while you talk to a citizen, e.g. \"@ Could you bake bread?\"."),
            List.of("Whoever runs the server sets the Gemini API key in the mod's config."));

    private static final RegistrationRegistry<AddonGuide> GUIDES = new RegistrationRegistry<>("guide");

    static {
        GUIDES.register(TALKING.id(), Integer.MIN_VALUE, TALKING);
    }

    @Override
    public @NotNull AddonRegistration register(@NotNull AddonGuide guide) {
        return GUIDES.register(guide.id(), 0, guide);
    }

    @Override
    public @NotNull List<AddonGuide> guides() {
        return registered();
    }

    /** Every guide, Talking Colonists' own first, then sorted by id. */
    public static @NotNull List<AddonGuide> registered() {
        return GUIDES.orderedSnapshot().stream().map(RegistrationRegistry.Entry::value).toList();
    }
}
