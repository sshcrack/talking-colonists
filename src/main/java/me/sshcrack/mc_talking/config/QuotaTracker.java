package me.sshcrack.mc_talking.config;

import java.util.concurrent.ConcurrentHashMap;

public final class QuotaTracker {
    private QuotaTracker() {}

    private static final ConcurrentHashMap<String, Long> exceededAt = new ConcurrentHashMap<>();
    // The quota is only the Tokens per minute, there are no daily / monthly limits
    private static final long QUOTA_WINDOW_MS = 60_000L;

    public static void reportQuotaExceeded(String modelName) {
        exceededAt.put(modelName, System.currentTimeMillis());
    }

    public static boolean isQuotaExceeded(String modelName) {
        Long ts = exceededAt.get(modelName);
        if (ts == null) return false;
        if (System.currentTimeMillis() - ts >= QUOTA_WINDOW_MS) {
            exceededAt.remove(modelName);
            return false;
        }
        return true;
    }

    public static void clear() {
        exceededAt.clear();
    }
}
