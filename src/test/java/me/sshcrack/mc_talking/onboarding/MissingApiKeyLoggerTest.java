package me.sshcrack.mc_talking.onboarding;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MissingApiKeyLogger} only exposes a static, log-only surface, so this test
 * verifies the observable once-per-feature behaviour: repeat calls for the same
 * feature name must not throw or otherwise change state, and {@link
 * MissingApiKeyLogger#reset()} must allow the feature to be reported again.
 */
class MissingApiKeyLoggerTest {
    @AfterEach
    void resetSharedState() {
        MissingApiKeyLogger.reset();
    }

    @Test
    void warnOnceDoesNotThrowForRepeatedCallsOfTheSameFeature() {
        MissingApiKeyLogger.warnOnce("casual_greeting");
        MissingApiKeyLogger.warnOnce("casual_greeting");
        MissingApiKeyLogger.warnOnce("casual_greeting");
    }

    @Test
    void warnOnceHandlesDistinctFeaturesIndependently() {
        MissingApiKeyLogger.warnOnce("casual_greeting");
        MissingApiKeyLogger.warnOnce("urgent_contact");
        MissingApiKeyLogger.warnOnce("pregeneration");
    }

    @Test
    void resetAllowsAFeatureToBeReportedAgain() {
        MissingApiKeyLogger.warnOnce("casual_greeting");
        MissingApiKeyLogger.reset();
        MissingApiKeyLogger.warnOnce("casual_greeting");
    }
}
