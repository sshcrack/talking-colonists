package me.sshcrack.mc_talking.config;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-model local retry throttle for Gemini quota/rate-limit errors.
 *
 * <p>Gemini limits can include RPM, TPM, RPD and model-specific dimensions, and
 * the exact free-tier limits vary by project/model. We therefore do not pretend
 * that every 429 resets after one minute. Repeated failures back off
 * progressively, while successful provider work resets the state.</p>
 */
public final class QuotaTracker {
    private QuotaTracker() {}

    private record QuotaState(int consecutiveFailures, long retryAtMs) {}

    private static final ConcurrentHashMap<String, QuotaState> states = new ConcurrentHashMap<>();

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long FIVE_MINUTES_MS = 5 * ONE_MINUTE_MS;
    private static final long FIFTEEN_MINUTES_MS = 15 * ONE_MINUTE_MS;
    private static final long ONE_HOUR_MS = 60 * ONE_MINUTE_MS;

    public static void reportQuotaExceeded(String modelName) {
        long now = System.currentTimeMillis();
        states.compute(modelName, (ignored, previous) -> {
            int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
            return new QuotaState(failures, now + backoffMs(failures));
        });
    }

    /** Reset the progressive backoff after the model successfully completes provider work. */
    public static void reportSuccess(String modelName) {
        states.remove(modelName);
    }

    public static boolean isQuotaExceeded(String modelName) {
        QuotaState state = states.get(modelName);
        return state != null && System.currentTimeMillis() < state.retryAtMs();
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
}
