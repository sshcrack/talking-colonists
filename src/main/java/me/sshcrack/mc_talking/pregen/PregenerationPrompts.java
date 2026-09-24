package me.sshcrack.mc_talking.pregen;

import me.sshcrack.mc_talking.api.pregen.PregenerationKind;

/** Prompt text for pregenerated (cached) citizen lines. */
public final class PregenerationPrompts {
    private PregenerationPrompts() {
    }

    public static String citizenGreeting(String friendName) {
        return "Generate a brief 1-sentence passing greeting for your friend " + friendName + ".";
    }

    /** Cached lines may play much later, so everything except threat exclaims avoids time-bound wording. */
    public static String withCacheSafetyNote(String prompt, PregenerationKind kind) {
        if (kind == PregenerationKind.THREAT) return prompt;
        return prompt + " IMPORTANT: this line is cached and may be heard much later. "
                + "Do not mention the current time of day, light level, weather, or a meal you are about to have; "
                + "use wording that remains true whenever the cached line is played.";
    }
}
