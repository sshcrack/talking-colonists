package me.sshcrack.mc_talking.config;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TtsQuotaManager {
    private static final long QUOTA_RETRY_INTERVAL_MS = 3_600_000;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final AtomicBoolean ttsFailed = new AtomicBoolean(false);
    private static final AtomicLong lastFailureTime = new AtomicLong(0);
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);

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

    public static boolean isTtsFailed() {
        if (ttsFailed.get() && System.currentTimeMillis() - lastFailureTime.get() > QUOTA_RETRY_INTERVAL_MS) {
            ttsFailed.set(false);
            consecutiveFailures.set(0);
        }
        return ttsFailed.get();
    }

    public static void reportFailure(Exception e) {
        consecutiveFailures.incrementAndGet();
        if (isQuotaError(e) || consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
            ttsFailed.set(true);
            lastFailureTime.set(System.currentTimeMillis());
        }
    }

    public static void reportSuccess() {
        ttsFailed.set(false);
        consecutiveFailures.set(0);
    }
}
