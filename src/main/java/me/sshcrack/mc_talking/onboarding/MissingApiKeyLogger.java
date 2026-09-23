package me.sshcrack.mc_talking.onboarding;

import me.sshcrack.mc_talking.McTalking;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logs "no Gemini API key configured" once per feature per server session instead of
 * silently skipping (or logging) on every tick/check.
 */
public final class MissingApiKeyLogger {
    private MissingApiKeyLogger() {
    }

    private static final Set<String> loggedFeatures = ConcurrentHashMap.newKeySet();

    /**
     * Logs a warning for {@code featureName} the first time it is reported this
     * session; subsequent calls for the same feature are no-ops.
     */
    public static void warnOnce(String featureName) {
        if (loggedFeatures.add(featureName)) {
            McTalking.LOGGER.warn("Skipping {} because no Gemini API key is configured.", featureName);
        }
    }

    /** Clears session state. Call when the server starts. */
    public static void reset() {
        loggedFeatures.clear();
    }
}
