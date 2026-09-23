package me.sshcrack.mc_talking.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaPlayerMessageThrottleTest {

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private final AtomicLong now = new AtomicLong(0);

    private void useFakeClock() {
        QuotaPlayerMessageThrottle.setClockForTesting(now::get);
    }

    @AfterEach
    void tearDown() {
        QuotaPlayerMessageThrottle.resetClockForTesting();
        QuotaPlayerMessageThrottle.clear();
    }

    @Test
    void firstNotificationIsAllowed() {
        useFakeClock();

        assertTrue(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
    }

    @Test
    void secondNotificationWithinWindowIsSuppressed() {
        useFakeClock();
        now.set(0L);

        assertTrue(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
        now.set(1_000L);
        assertFalse(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
    }

    @Test
    void notificationAllowedAgainAfterWindowElapses() {
        useFakeClock();
        now.set(0L);

        assertTrue(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
        now.set(30_001L);
        assertTrue(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
    }

    @Test
    void throttleIsPerPlayer() {
        useFakeClock();
        now.set(0L);

        assertTrue(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
        assertTrue(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_B));
        assertFalse(QuotaPlayerMessageThrottle.shouldNotify(PLAYER_A));
    }
}
