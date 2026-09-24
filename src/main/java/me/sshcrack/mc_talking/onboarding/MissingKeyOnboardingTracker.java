package me.sshcrack.mc_talking.onboarding;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether a player should be shown the missing-Gemini-API-key onboarding
 * message: only operators (or the single-player/LAN host), and only once per
 * player for the lifetime of the currently running server.
 *
 * <p>Kept free of any Minecraft types so the once-per-operator-per-session rule can
 * be unit tested directly.</p>
 */
public final class MissingKeyOnboardingTracker {
    private final Set<UUID> notifiedThisSession = ConcurrentHashMap.newKeySet();

    /**
     * @param playerId   the joining player's UUID
     * @param isOperator whether the player is an operator (permission level >= 2)
     *                   or the single-player/LAN host
     * @return {@code true} the first time this is called for a given operator during
     * this server session; {@code false} for non-operators and for repeat calls for
     * an operator who was already notified
     */
    public boolean shouldNotify(UUID playerId, boolean isOperator) {
        if (playerId == null || !isOperator) {
            return false;
        }
        return notifiedThisSession.add(playerId);
    }

    /** Clears session state. Call when the server starts (and/or stops). */
    public void reset() {
        notifiedThisSession.clear();
    }
}
