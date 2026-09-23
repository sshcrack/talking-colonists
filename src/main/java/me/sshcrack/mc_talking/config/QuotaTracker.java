package me.sshcrack.mc_talking.config;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Per-model local retry throttle for Gemini quota/rate-limit errors.
 *
 * <p>Gemini limits can include RPM, TPM, RPD and model-specific dimensions, and
 * the exact free-tier limits vary by project/model. We therefore do not pretend
 * that every 429 resets after one minute. Repeated failures back off
 * progressively, while successful provider work resets the state.</p>
 *
 * <p>State is kept as an immutable {@link QuotaSnapshot} per model so callers - including a
 * later read-only addon API (A7) - only ever observe a consistent point-in-time view.</p>
 */
public final class QuotaTracker {
    private QuotaTracker() {}

    private record QuotaState(int consecutiveFailures, long sinceMs, long retryAtMs, @Nullable Long providerResetAtMs) {}

    private static final ConcurrentHashMap<String, QuotaState> states = new ConcurrentHashMap<>();

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS;
    private static final long FIFTEEN_MINUTES_MS = 15 * ONE_MINUTE_MS;
    private static final long ONE_HOUR_MS = 60 * ONE_MINUTE_MS;

    private static volatile LongSupplier clock = System::currentTimeMillis;

    public static void reportQuotaExceeded(String modelName) {
        reportQuotaExceeded(modelName, null);
    }

    /**
     * @param providerRetryAfterMs how long the provider itself said to wait, if known (for
     *                             example from a {@code RetryInfo} detail); {@code null} when
     *                             the error carried no such hint
     */
    public static void reportQuotaExceeded(String modelName, @Nullable Long providerRetryAfterMs) {
        long now = clock.getAsLong();
        states.compute(modelName, (ignored, previous) -> {
            int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
            long since = previous == null ? now : previous.sinceMs();
            long backoffRetryAt = now + backoffMs(failures);
            Long providerResetAt = providerRetryAfterMs == null ? null : now + providerRetryAfterMs;
            long retryAt = providerResetAt == null ? backoffRetryAt : Math.max(backoffRetryAt, providerResetAt);
            return new QuotaState(failures, since, retryAt, providerResetAt);
        });
    }

    /** Reset the progressive backoff after the model successfully completes provider work. */
    public static void reportSuccess(String modelName) {
        states.remove(modelName);
    }

    public static boolean isQuotaExceeded(String modelName) {
        QuotaState state = states.get(modelName);
        return state != null && clock.getAsLong() < state.retryAtMs();
    }

    /** Immutable point-in-time view of a single model's quota state. */
    public static QuotaSnapshot snapshot(String modelName) {
        long now = clock.getAsLong();
        QuotaState state = states.get(modelName);
        if (state == null || now >= state.retryAtMs()) {
            return new QuotaSnapshot(modelName, QuotaStatus.OK, state == null ? now : state.sinceMs(), null);
        }
        return new QuotaSnapshot(modelName, QuotaStatus.EXHAUSTED, state.sinceMs(), state.providerResetAtMs());
    }

    /** Immutable snapshot of every model that has ever recorded a quota failure. */
    public static Map<String, QuotaSnapshot> snapshotAll() {
        return states.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(model -> model, QuotaTracker::snapshot));
    }

    private static long backoffMs(int failures) {
        return switch (failures) {
            case 1 -> ONE_MINUTE_MS;
            case 2 -> FIVE_MINUTES_MS;
            case 3 -> FIFTEEN_MINUTES_MS;
            default -> ONE_HOUR_MS;
        };
    }

    public static void clear() {
        states.clear();
    }

    /** Test seam only: package-private so unit tests can drive time deterministically. */
    static void setClockForTesting(LongSupplier supplier) {
        clock = supplier;
    }

    /** Test seam only: restores the wall clock after a test replaces it. */
    static void resetClockForTesting() {
        clock = System::currentTimeMillis;
    }
}
