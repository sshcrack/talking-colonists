package me.sshcrack.mc_talking.internal.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackDrainCoordinatorTest {
    @Test
    void gracefulCloseFlushesFinalBuffersAndWaitsUntilPlaybackDrains() {
        AtomicBoolean pending = new AtomicBoolean(true);
        AtomicInteger flushes = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Runnable> timeout = new AtomicReference<>();
        AtomicBoolean timeoutCancelled = new AtomicBoolean(false);

        var coordinator = new PlaybackDrainCoordinator(
                flushes::incrementAndGet,
                pending::get,
                30_000,
                (task, delay) -> {
                    timeout.set(task);
                    return () -> timeoutCancelled.set(true);
                },
                completions::incrementAndGet);

        assertTrue(coordinator.request());
        assertEquals(1, flushes.get());
        assertEquals(0, completions.get(), "close must wait for fake audio to drain");
        assertNotNull(timeout.get());

        pending.set(false);
        coordinator.onPlaybackDrained();
        assertEquals(1, completions.get());
        assertTrue(timeoutCancelled.get());
        coordinator.onPlaybackDrained();
        assertEquals(1, completions.get(), "audible completion is exactly-once");
    }

    @Test
    void gracefulCloseTimeoutIsBoundedAndStillCompletesOnlyOnce() {
        AtomicBoolean pending = new AtomicBoolean(true);
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Runnable> timeout = new AtomicReference<>();
        AtomicLong scheduledDelay = new AtomicLong();

        var coordinator = new PlaybackDrainCoordinator(
                () -> { },
                pending::get,
                30_000,
                (task, delay) -> {
                    timeout.set(task);
                    scheduledDelay.set(delay);
                    return () -> { };
                },
                completions::incrementAndGet);

        assertTrue(coordinator.request());
        assertEquals(30_000, scheduledDelay.get());
        timeout.get().run();
        assertEquals(1, completions.get(), "bounded timeout must force lifecycle completion");
        pending.set(false);
        coordinator.onPlaybackDrained();
        assertEquals(1, completions.get());
    }

    @Test
    void repeatedEndSignalsYieldOneFlushOneTimerAndOneCompletion() {
        AtomicBoolean pending = new AtomicBoolean(true);
        AtomicInteger flushes = new AtomicInteger();
        AtomicInteger schedules = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Runnable> timeout = new AtomicReference<>();

        var coordinator = new PlaybackDrainCoordinator(
                flushes::incrementAndGet,
                pending::get,
                5_000,
                (task, delay) -> {
                    schedules.incrementAndGet();
                    timeout.set(task);
                    return () -> { };
                },
                completions::incrementAndGet);

        assertTrue(coordinator.request());
        assertFalse(coordinator.request());
        assertFalse(coordinator.request());
        assertEquals(1, flushes.get());
        assertEquals(1, schedules.get());

        timeout.get().run();
        coordinator.onPlaybackDrained();
        assertEquals(1, completions.get());
    }
}
