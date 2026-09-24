package me.sshcrack.mc_talking.config;

import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsQuotaManagerTest {

    private final AtomicLong now = new AtomicLong(0);

    private void useFakeClock() {
        TtsQuotaManager.setClockForTesting(now::get);
    }

    @AfterEach
    void tearDown() {
        TtsQuotaManager.resetClockForTesting();
        TtsQuotaManager.clear();
    }

    @Test
    void startsOk() {
        useFakeClock();

        assertFalse(TtsQuotaManager.isTtsFailed());
        assertEquals(QuotaStatus.OK, TtsQuotaManager.snapshot().status());
    }

    @Test
    void quotaMessageMarksFailedImmediately() {
        useFakeClock();
        now.set(1_000L);

        TtsQuotaManager.reportFailure(new RuntimeException("429 Too Many Requests"));

        assertTrue(TtsQuotaManager.isTtsFailed());
        QuotaSnapshot snapshot = TtsQuotaManager.snapshot();
        assertEquals(QuotaStatus.EXHAUSTED, snapshot.status());
        assertEquals(1_000L, snapshot.sinceMs());
    }

    @Test
    void nonQuotaFailuresBelowThresholdDoNotTripFailure() {
        useFakeClock();

        TtsQuotaManager.reportFailure(new RuntimeException("boom"));
        TtsQuotaManager.reportFailure(new RuntimeException("boom again"));

        assertFalse(TtsQuotaManager.isTtsFailed());
    }

    @Test
    void thirdConsecutiveNonQuotaFailureTripsFailure() {
        useFakeClock();

        TtsQuotaManager.reportFailure(new RuntimeException("boom"));
        TtsQuotaManager.reportFailure(new RuntimeException("boom again"));
        TtsQuotaManager.reportFailure(new RuntimeException("boom a third time"));

        assertTrue(TtsQuotaManager.isTtsFailed());
    }

    @Test
    void resetsAfterRetryIntervalElapses() {
        useFakeClock();
        now.set(0L);

        TtsQuotaManager.reportFailure(new RuntimeException("quota exceeded"));
        assertTrue(TtsQuotaManager.isTtsFailed());

        now.set(3_600_000L + 1);

        assertFalse(TtsQuotaManager.isTtsFailed());
        assertEquals(QuotaStatus.OK, TtsQuotaManager.snapshot().status());
    }

    @Test
    void reportSuccessClearsFailureImmediately() {
        useFakeClock();
        now.set(0L);

        TtsQuotaManager.reportFailure(new RuntimeException("quota exceeded"));
        assertTrue(TtsQuotaManager.isTtsFailed());

        TtsQuotaManager.reportSuccess();

        assertFalse(TtsQuotaManager.isTtsFailed());
    }

    @Test
    void providerRetryDelayIsExposedAsResetEstimate() {
        useFakeClock();
        now.set(10_000L);

        UnexpectedResponseException e = new UnexpectedResponseException(
                "quota exceeded",
                429,
                "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\",\"details\":"
                        + "[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"31s\"}]}}");

        TtsQuotaManager.reportFailure(e);

        QuotaSnapshot snapshot = TtsQuotaManager.snapshot();
        assertEquals(QuotaStatus.EXHAUSTED, snapshot.status());
        assertEquals(10_000L + 31_000L, snapshot.resetAtMs());
    }

    @Test
    void unknownResetWhenProviderGivesNoRetryInfo() {
        useFakeClock();
        now.set(0L);

        TtsQuotaManager.reportFailure(new RuntimeException("quota exceeded"));

        assertNull(TtsQuotaManager.snapshot().resetAtMs());
    }

    @Test
    void ttsQuotaIsIndependentOfLiveModelQuotaTracker() {
        useFakeClock();
        QuotaTracker.setClockForTesting(now::get);
        try {
            now.set(0L);

            TtsQuotaManager.reportFailure(new RuntimeException("quota exceeded"));

            assertTrue(TtsQuotaManager.isTtsFailed());
            assertFalse(QuotaTracker.isQuotaExceeded("gemini-3.1-flash-live-preview"));
        } finally {
            QuotaTracker.resetClockForTesting();
            QuotaTracker.clear();
        }
    }
}
