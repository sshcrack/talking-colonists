package me.sshcrack.mc_talking.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaTrackerTest {

    private final AtomicLong now = new AtomicLong(0);

    private void useFakeClock() {
        QuotaTracker.setClockForTesting(now::get);
    }

    @AfterEach
    void tearDown() {
        QuotaTracker.resetClockForTesting();
        QuotaTracker.clear();
    }

    @Test
    void modelStartsOkWithUnknownReset() {
        useFakeClock();

        QuotaSnapshot snapshot = QuotaTracker.snapshot("model-a");

        assertEquals(QuotaStatus.OK, snapshot.status());
        assertNull(snapshot.resetAtMs());
        assertFalse(QuotaTracker.isQuotaExceeded("model-a"));
    }

    @Test
    void exceededThenResetTransitionsBackToOk() {
        useFakeClock();
        now.set(1_000L);

        QuotaTracker.reportQuotaExceeded("model-a");
        assertTrue(QuotaTracker.isQuotaExceeded("model-a"));
        QuotaSnapshot exhausted = QuotaTracker.snapshot("model-a");
        assertEquals(QuotaStatus.EXHAUSTED, exhausted.status());
        assertEquals(1_000L, exhausted.sinceMs());

        // Backoff for the first failure is one minute; advance just past it.
        now.set(1_000L + 60_000L + 1);

        assertFalse(QuotaTracker.isQuotaExceeded("model-a"));
        assertEquals(QuotaStatus.OK, QuotaTracker.snapshot("model-a").status());
    }

    @Test
    void reportSuccessImmediatelyClearsExceededState() {
        useFakeClock();
        now.set(5_000L);

        QuotaTracker.reportQuotaExceeded("model-a");
        assertTrue(QuotaTracker.isQuotaExceeded("model-a"));

        QuotaTracker.reportSuccess("model-a");

        assertFalse(QuotaTracker.isQuotaExceeded("model-a"));
        assertEquals(QuotaStatus.OK, QuotaTracker.snapshot("model-a").status());
    }

    @Test
    void perModelStateIsIndependent() {
        useFakeClock();
        now.set(0L);

        QuotaTracker.reportQuotaExceeded("model-a");

        assertTrue(QuotaTracker.isQuotaExceeded("model-a"));
        assertFalse(QuotaTracker.isQuotaExceeded("model-b"));
        assertEquals(QuotaStatus.OK, QuotaTracker.snapshot("model-b").status());
    }

    @Test
    void repeatedFailuresBackOffProgressively() {
        useFakeClock();
        now.set(0L);

        QuotaTracker.reportQuotaExceeded("model-a"); // 1 minute
        now.set(60_001L);
        assertFalse(QuotaTracker.isQuotaExceeded("model-a"));

        QuotaTracker.reportQuotaExceeded("model-a"); // 2nd consecutive failure -> 5 minutes
        assertTrue(QuotaTracker.isQuotaExceeded("model-a"));
        now.set(60_001L + 5 * 60_000L - 1);
        assertTrue(QuotaTracker.isQuotaExceeded("model-a"));
        now.set(60_001L + 5 * 60_000L + 1);
        assertFalse(QuotaTracker.isQuotaExceeded("model-a"));
    }

    @Test
    void providerRetryAfterIsExposedAsResetEstimate() {
        useFakeClock();
        now.set(2_000L);

        QuotaTracker.reportQuotaExceeded("model-a", 45_000L);

        QuotaSnapshot snapshot = QuotaTracker.snapshot("model-a");
        assertEquals(QuotaStatus.EXHAUSTED, snapshot.status());
        assertEquals(2_000L + 45_000L, snapshot.resetAtMs());
    }

    @Test
    void snapshotAllOnlyIncludesTrackedModels() {
        useFakeClock();
        now.set(0L);

        QuotaTracker.reportQuotaExceeded("model-a");

        var all = QuotaTracker.snapshotAll();

        assertEquals(1, all.size());
        assertTrue(all.containsKey("model-a"));
        assertEquals(QuotaStatus.EXHAUSTED, all.get("model-a").status());
    }
}
