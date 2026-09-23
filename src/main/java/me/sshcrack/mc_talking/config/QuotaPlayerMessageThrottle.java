package me.sshcrack.mc_talking.config;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Limits how often a single player is shown the "quota exhausted" action-bar message, so a
 * citizen retrying (or several citizens failing back to back) does not spam the player's screen.
 */
public final class QuotaPlayerMessageThrottle {
    private QuotaPlayerMessageThrottle() {
    }

    private static final long WINDOW_MS = 30_000L;

    private static final ConcurrentHashMap<UUID, Long> lastNotifiedAtMs = new ConcurrentHashMap<>();

    private static volatile LongSupplier clock = System::currentTimeMillis;

    /**
     * @return {@code true} if the player has not been notified within the throttle window (and
     * records that they are being notified now), {@code false} if a notification should be
     * suppressed
     */
    public static boolean shouldNotify(UUID playerId) {
        long now = clock.getAsLong();
        boolean[] allowed = new boolean[1];
        lastNotifiedAtMs.compute(playerId, (id, last) -> {
            if (last != null && now - last < WINDOW_MS) {
                allowed[0] = false;
                return last;
            }
            allowed[0] = true;
            return now;
        });
        return allowed[0];
    }

    public static void clear() {
        lastNotifiedAtMs.clear();
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
