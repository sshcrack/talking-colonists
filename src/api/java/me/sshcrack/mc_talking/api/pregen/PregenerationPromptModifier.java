package me.sshcrack.mc_talking.api.pregen;

import org.jetbrains.annotations.NotNull;

/**
 * Transforms a pregenerated line directive before it is sent to Gemini. Useful for constraints
 * such as making cached greetings time-independent without mixing into pregeneration internals.
 */
@FunctionalInterface
public interface PregenerationPromptModifier {
    @NotNull String modify(@NotNull PregenerationPromptContext context, @NotNull String prompt);
}
