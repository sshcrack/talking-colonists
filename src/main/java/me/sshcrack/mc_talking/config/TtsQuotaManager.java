package me.sshcrack.mc_talking.config;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Tracks quota/rate-limit failures for the TTS (and Flash script) HTTP pipeline, independently
 * of {@link QuotaTracker}'s per-Live-model state: TTS uses a separate REST endpoint with its own
 * limits, so a Live session being fine says nothing about whether TTS generation currently works,
 * and vice versa.
 */
public class TtsQuotaManager {
    private static final long QUOTA_RETRY_INTERVAL_MS = 3_600_000;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    /** Label used as the "model" of the {@link QuotaSnapshot} this manager reports. */
    public static final String LABEL = "tts";

    private record State(int consecutiveFailures, long sinceMs, long retryAtMs, @Nullable Long providerResetAtMs) {}

    private static final AtomicReference<State> state = new AtomicReference<>();

    private static volatile LongSupplier clock = System::currentTimeMillis;

    private static boolean isQuotaError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        msg = msg.toLowerCase();
        return msg.contains("quota")
                || msg.contains("resource exhausted")
                || msg.contains("rate limit")
                || msg.contains("429")
                || msg.contains("too many requests");
    }

    @Nullable
    private static Long extractProviderRetryAfterMs(Exception e) {
        if (!(e instanceof me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException ure)) return null;
        return QuotaRetryInfo.parseRetryDelayMs(ure.getResponseBody());
    }

    public static boolean isTtsFailed() {
        State current = state.get();
        if (current == null) return false;
        if (clock.getAsLong() >= current.retryAtMs()) {
            state.compareAndSet(current, null);
            return false;
        }
        return true;
    }

    public static void reportFailure(Exception e) {
        long now = clock.getAsLong();
        Long providerRetryAfterMs = extractProviderRetryAfterMs(e);
        boolean quotaError = isQuotaError(e);
        state.updateAndGet(previous -> {
            int failures = previous == null ? 1 : previous.consecutiveFailures() + 1;
            long since = previous == null ? now : previous.sinceMs();
            if (!quotaError && failures < MAX_CONSECUTIVE_FAILURES) {
                // Not yet treated as a quota failure: keep the failure count but do not block.
                return new State(failures, since, now, null);
            }
            Long providerResetAt = providerRetryAfterMs == null ? null : now + providerRetryAfterMs;
            long retryAt = providerResetAt == null ? now + QUOTA_RETRY_INTERVAL_MS : Math.max(now + QUOTA_RETRY_INTERVAL_MS, providerResetAt);
            return new State(failures, since, retryAt, providerResetAt);
        });
    }

    public static void reportSuccess() {
        state.set(null);
    }

    /** Immutable point-in-time view of the TTS pipeline's quota state. */
    public static QuotaSnapshot snapshot() {
        long now = clock.getAsLong();
        State current = state.get();
        if (current == null || now >= current.retryAtMs()) {
            return new QuotaSnapshot(LABEL, QuotaStatus.OK, current == null ? now : current.sinceMs(), null);
        }
        return new QuotaSnapshot(LABEL, QuotaStatus.EXHAUSTED, current.sinceMs(), current.providerResetAtMs());
    }

    public static void clear() {
        state.set(null);
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
