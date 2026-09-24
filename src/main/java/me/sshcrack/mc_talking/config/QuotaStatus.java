package me.sshcrack.mc_talking.config;

/**
 * Coarse quota state for a single provider model (or the TTS pipeline).
 *
 * <p>Kept intentionally small so a later read-only addon API (roadmap task A7) can wrap it
 * without change: addons only ever need to know whether work can currently be scheduled.</p>
 */
public enum QuotaStatus {
    /** No active exceedance is being tracked; work may be attempted. */
    OK,
    /** A quota/rate-limit failure is currently in effect. */
    EXHAUSTED
}
