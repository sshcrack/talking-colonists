package me.sshcrack.mc_talking.conversations;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationCancellationTest {
    @Test
    void cancellingAfterLiveRegistrationRunsCleanupExactlyOnce() {
        ConversationCancellation cancellation = new ConversationCancellation();
        AtomicInteger cleanups = new AtomicInteger();

        assertTrue(cancellation.registerLiveCancellation(cleanups::incrementAndGet));
        cancellation.cancel();
        cancellation.cancel();

        assertTrue(cancellation.isCancelled());
        assertEquals(1, cleanups.get());
    }

    @Test
    void cancellingBeforeLiveRegistrationPreventsLateSessionStartup() {
        ConversationCancellation cancellation = new ConversationCancellation();
        AtomicInteger cleanups = new AtomicInteger();

        cancellation.cancel();

        assertFalse(cancellation.registerLiveCancellation(cleanups::incrementAndGet));
        assertEquals(1, cleanups.get());
    }

    @Test
    void completedLiveSessionCanClearItsCancellationAction() {
        ConversationCancellation cancellation = new ConversationCancellation();
        AtomicInteger cleanups = new AtomicInteger();
        Runnable action = cleanups::incrementAndGet;

        assertTrue(cancellation.registerLiveCancellation(action));
        cancellation.clearLiveCancellation(action);
        cancellation.cancel();

        assertEquals(0, cleanups.get());
    }
}
