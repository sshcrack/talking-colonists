package me.sshcrack.mc_talking.api.provider;

/** Coarse provider quota state for one model. */
public enum ProviderQuotaState {
    /** No active rate-limit or quota failure; work may be attempted. */
    OK,
    /** A quota or rate-limit failure is in effect; see {@link ModelQuotaView#exhaustedUntil()}. */
    EXHAUSTED,
    /** Cannot be known right now, for example because no API key is configured. */
    UNKNOWN
}
