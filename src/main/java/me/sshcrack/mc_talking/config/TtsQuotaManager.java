package me.sshcrack.mc_talking.config;

public class TtsQuotaManager {
    private static final long QUOTA_RETRY_INTERVAL_MS = 3_600_000;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static boolean ttsFailed = false;
    private static long lastFailureTime = 0;
    private static int consecutiveFailures = 0;

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
        if (ttsFailed && System.currentTimeMillis() - lastFailureTime > QUOTA_RETRY_INTERVAL_MS) {
            ttsFailed = false;
            consecutiveFailures = 0;
        }
        return ttsFailed;
    }

    public static void reportFailure(Exception e) {
        consecutiveFailures++;
        if (isQuotaError(e) || consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            ttsFailed = true;
            lastFailureTime = System.currentTimeMillis();
        }
    }

    public static void reportSuccess() {
        consecutiveFailures = 0;
    }
}
