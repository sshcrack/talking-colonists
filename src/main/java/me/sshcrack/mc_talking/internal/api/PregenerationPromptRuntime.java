package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.pregen.PregenerationPromptContext;
import me.sshcrack.mc_talking.api.pregen.PregenerationPromptModifier;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime-only storage/evaluation for addon pregeneration prompt modifiers. */
public final class PregenerationPromptRuntime {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final int MAX_PROMPT_CHARS = 16_000;
    private static final RegistrationRegistry<PregenerationPromptModifier> MODIFIERS =
            new RegistrationRegistry<>("Pregeneration prompt modifier");

    private PregenerationPromptRuntime() {
    }

    public static @NotNull AddonRegistration register(
            @NotNull String id,
            int order,
            @NotNull PregenerationPromptModifier modifier
    ) {
        return MODIFIERS.register(id, order, modifier);
    }

    public static @NotNull String apply(
            @NotNull PregenerationPromptContext context,
            @NotNull String originalPrompt
    ) {
        String prompt = java.util.Objects.requireNonNull(originalPrompt, "originalPrompt");
        for (var registration : MODIFIERS.orderedSnapshot()) {
            try {
                String modified = registration.value().modify(context, prompt);
                if (modified == null || modified.isBlank()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Pregeneration prompt modifier {0} returned an empty prompt; ignoring it", registration.id());
                    continue;
                }
                if (modified.length() > MAX_PROMPT_CHARS) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Pregeneration prompt modifier {0} exceeded {1} chars; truncating",
                            registration.id(), MAX_PROMPT_CHARS);
                    modified = modified.substring(0, MAX_PROMPT_CHARS);
                }
                prompt = modified;
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Pregeneration prompt modifier " + registration.id() + " failed and was skipped", t);
            }
        }
        return prompt;
    }
}
