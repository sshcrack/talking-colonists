package me.sshcrack.mc_talking.onboarding;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingKeyOnboardingTrackerTest {
    @Test
    void nonOperatorIsNeverNotified() {
        var tracker = new MissingKeyOnboardingTracker();
        UUID player = UUID.randomUUID();

        assertFalse(tracker.shouldNotify(player, false));
        assertFalse(tracker.shouldNotify(player, false), "repeat check for the same non-operator must stay false");
    }

    @Test
    void operatorIsNotifiedOnceThenSuppressedForTheRestOfTheSession() {
        var tracker = new MissingKeyOnboardingTracker();
        UUID op = UUID.randomUUID();

        assertTrue(tracker.shouldNotify(op, true), "first check for a fresh operator should notify");
        assertFalse(tracker.shouldNotify(op, true), "second check for the same operator must not notify again");
        assertFalse(tracker.shouldNotify(op, true), "third check for the same operator must not notify again");
    }

    @Test
    void eachDistinctOperatorIsNotifiedOnceIndependently() {
        var tracker = new MissingKeyOnboardingTracker();
        UUID opOne = UUID.randomUUID();
        UUID opTwo = UUID.randomUUID();

        assertTrue(tracker.shouldNotify(opOne, true));
        assertTrue(tracker.shouldNotify(opTwo, true));
        assertFalse(tracker.shouldNotify(opOne, true));
        assertFalse(tracker.shouldNotify(opTwo, true));
    }

    @Test
    void resetClearsSessionStateForANewServerRun() {
        var tracker = new MissingKeyOnboardingTracker();
        UUID op = UUID.randomUUID();

        assertTrue(tracker.shouldNotify(op, true));
        assertFalse(tracker.shouldNotify(op, true));

        tracker.reset();

        assertTrue(tracker.shouldNotify(op, true), "after reset the same operator should be notified again");
    }

    @Test
    void nullPlayerIdIsNeverNotified() {
        var tracker = new MissingKeyOnboardingTracker();

        assertFalse(tracker.shouldNotify(null, true));
    }
}
