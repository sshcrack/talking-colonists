package me.sshcrack.mc_talking.config;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable point-in-time view of a single model's (or the TTS pipeline's) quota state.
 *
 * <p>This is the shape roadmap task A7 will expose read-only through
 * {@code ProviderBudgetService}: nothing here should be mutated by a caller, and nothing here
 * should change shape without checking that plan.</p>
 *
 * @param model     the provider model name (or a fixed label such as {@code "tts"}) this
 *                  snapshot describes
 * @param status    whether the model is currently usable
 * @param sinceMs   epoch millis when this status started being tracked: the first failure of
 *                  the current exceedance streak when {@link #status} is {@link QuotaStatus#EXHAUSTED},
 *                  or the last time the state returned to {@link QuotaStatus#OK} otherwise
 * @param resetAtMs epoch millis when the provider itself said quota would reset (parsed from a
 *                  retry-after/{@code RetryInfo} hint), or {@code null} when no such hint was
 *                  available and the reset time is therefore unknown. This is deliberately
 *                  {@code null} rather than our own progressive-backoff guess: a local guess is
 *                  not something we should present to players or addons as an authoritative
 *                  estimate.
 */
public record QuotaSnapshot(String model, QuotaStatus status, long sinceMs, @Nullable Long resetAtMs) {
}
