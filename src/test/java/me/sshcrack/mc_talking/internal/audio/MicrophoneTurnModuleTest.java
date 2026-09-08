package me.sshcrack.mc_talking.internal.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrophoneTurnModuleTest {
    private static final MicrophoneTurnModule.Timing TIMING = new MicrophoneTurnModule.Timing(
            100,
            20,
            200,
            500,
            2,
            960
    );

    @Test
    void speechPauseResponsePaddingCannotInterruptResponse() {
        Harness harness = new Harness();
        harness.module.acceptMicrophone(speech());
        harness.module.acceptMicrophone(speech());
        assertEquals(0, harness.provider.playbackCancellations);

        harness.scheduler.advanceBy(100);
        assertEquals(List.of(true), harness.provider.waiting);
        int paddingBeforeResponse = harness.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING);
        assertTrue(paddingBeforeResponse > 0);

        harness.provider.playbackActive = true;
        harness.scheduler.advanceBy(80);
        assertEquals(0, harness.provider.playbackCancellations,
                "generated padding must never become local barge-in evidence");

        harness.module.providerProgress(MicrophoneTurnModule.ProviderProgress.RESPONSE_STARTED);
        assertEquals(List.of(true, false), harness.provider.waiting);
        int paddingAtResponse = harness.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING);
        harness.scheduler.advanceBy(500);
        assertEquals(paddingAtResponse, harness.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING));
        assertEquals(0, harness.provider.playbackCancellations);
    }

    @Test
    void speechResumesBeforeOlderPaddingDeadlineOldCleanupCannotEndNewTurn() {
        Harness harness = new Harness();
        harness.module.acceptMicrophone(speech());
        UUID first = harness.module.currentTurnId();
        harness.scheduler.advanceBy(100);
        harness.scheduler.advanceBy(50);

        harness.module.acceptMicrophone(speech());
        UUID second = harness.module.currentTurnId();
        assertNotEquals(first, second);
        assertEquals(List.of(true, false), harness.provider.waiting,
                "replacement speech retires the older closed turn before starting its own timer");

        assertTrue(harness.scheduler.forceRunOneCancelledTask(),
                "test must execute a cancelled old callback to verify identity checks, not cancellation alone");
        assertEquals(second, harness.module.currentTurnId());

        harness.scheduler.advanceBy(100);
        assertEquals(List.of(true, false, true), harness.provider.waiting);
        int paddingBeforeOldDeadline = harness.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING);

        // The old turn's padding-stop deadline was t=300. The replacement's padding runs until t=450.
        harness.scheduler.advanceBy(70);
        int atOldDeadline = harness.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING);
        harness.scheduler.advanceBy(40);
        assertTrue(harness.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING) > atOldDeadline,
                "an old cleanup deadline must not stop replacement-turn padding");
        assertTrue(atOldDeadline >= paddingBeforeOldDeadline);
    }

    @Test
    void quietMicrophonePacketsDoNotRepeatedlyCancelOutput() {
        Harness harness = new Harness();
        harness.provider.playbackActive = true;
        for (int i = 0; i < 50; i++) harness.module.acceptMicrophone(quiet());

        assertEquals(0, harness.provider.playbackCancellations);
        assertEquals(50, harness.provider.count(MicrophoneTurnModule.InputKind.QUIET_MICROPHONE));
        assertEquals(0, harness.provider.count(MicrophoneTurnModule.InputKind.SPEECH));
    }

    @Test
    void genuineBargeInPromptlyStopsCurrentAudibleTurn() {
        Harness harness = new Harness();
        harness.provider.playbackActive = true;

        harness.module.acceptMicrophone(speech());
        assertEquals(0, harness.provider.playbackCancellations, "one frame is intentionally debounced");
        harness.module.acceptMicrophone(speech());
        assertEquals(1, harness.provider.playbackCancellations);

        harness.module.acceptMicrophone(speech());
        assertEquals(1, harness.provider.playbackCancellations, "one microphone turn cancels playback at most once");
    }

    @Test
    void speechKeepsBargeInRetryableUntilThereIsActuallyPlaybackToCancel() {
        Harness harness = new Harness();
        harness.module.acceptMicrophone(speech());
        harness.module.acceptMicrophone(speech());
        assertEquals(0, harness.provider.playbackCancellations);

        harness.provider.playbackActive = true;
        harness.module.acceptMicrophone(speech());
        assertEquals(1, harness.provider.playbackCancellations);
        harness.module.acceptMicrophone(speech());
        assertEquals(1, harness.provider.playbackCancellations);
    }

    @Test
    void sessionReplacementRejectsOldTimersAndTheirGeneratedInput() {
        Harness old = new Harness();
        old.module.acceptMicrophone(speech());
        old.scheduler.advanceBy(100);
        int oldPadding = old.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING);
        assertTrue(oldPadding > 0);

        old.module.close();
        assertEquals(0, old.scheduler.activeTasks());
        old.scheduler.advanceBy(1000);
        assertEquals(oldPadding, old.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING));

        Harness replacement = new Harness();
        replacement.module.acceptMicrophone(speech());
        assertNotEquals(old.sessionId, replacement.sessionId);
        assertEquals(1, replacement.provider.count(MicrophoneTurnModule.InputKind.SPEECH));
        assertEquals(oldPadding, old.provider.count(MicrophoneTurnModule.InputKind.GENERATED_PADDING));
    }

    @Test
    void shutdownLeavesNoScheduledAudioWork() {
        Harness harness = new Harness();
        harness.module.acceptMicrophone(speech());
        assertTrue(harness.scheduler.activeTasks() > 0);

        harness.module.close();
        assertEquals(0, harness.scheduler.activeTasks());
        int inputsAtClose = harness.provider.inputs.size();
        harness.scheduler.advanceBy(1000);
        assertEquals(inputsAtClose, harness.provider.inputs.size());
        assertFalse(harness.module.hasOwnedScheduledWork());
    }

    @Test
    void missingProviderProgressTimesOutTruthfullyInsteadOfThinkingForever() {
        Harness harness = new Harness();
        harness.module.acceptMicrophone(speech());
        harness.scheduler.advanceBy(100);
        assertEquals(List.of(true), harness.provider.waiting);

        harness.scheduler.advanceBy(500);
        assertEquals(List.of(true, false), harness.provider.waiting);
        assertTrue(harness.provider.diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.event() == MicrophoneTurnModule.DiagnosticEvent.RESPONSE_TIMEOUT));
        assertEquals(0, harness.scheduler.activeTasks());
    }

    private static short[] speech() {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (i % 2 == 0 ? 2200 : -2200);
        return pcm;
    }

    private static short[] quiet() {
        return new short[960];
    }

    private static final class Harness {
        final UUID sessionId = UUID.randomUUID();
        final ManualScheduler scheduler = new ManualScheduler();
        final FakeProvider provider = new FakeProvider();
        final MicrophoneTurnModule module = new MicrophoneTurnModule(
                sessionId,
                scheduler.clock::get,
                scheduler,
                provider,
                TIMING
        );
    }

    private static final class FakeProvider implements MicrophoneTurnModule.ProviderAdapter {
        final List<MicrophoneTurnModule.InputKind> inputs = new ArrayList<>();
        final List<Boolean> waiting = new ArrayList<>();
        final List<MicrophoneTurnModule.Diagnostic> diagnostics = new ArrayList<>();
        boolean playbackActive;
        int playbackCancellations;

        @Override
        public void acceptAudio(UUID sessionId, UUID turnId, MicrophoneTurnModule.InputKind kind, short[] pcm) {
            inputs.add(kind);
        }

        @Override
        public boolean cancelPlayback(UUID sessionId, UUID turnId) {
            if (!playbackActive) return false;
            playbackActive = false;
            playbackCancellations++;
            return true;
        }

        @Override
        public void waitingForResponse(UUID sessionId, UUID turnId, boolean waiting) {
            this.waiting.add(waiting);
        }

        @Override
        public void diagnostic(MicrophoneTurnModule.Diagnostic diagnostic) {
            diagnostics.add(diagnostic);
        }

        int count(MicrophoneTurnModule.InputKind kind) {
            int count = 0;
            for (MicrophoneTurnModule.InputKind input : inputs) if (input == kind) count++;
            return count;
        }
    }

    private static final class ManualScheduler implements MicrophoneTurnModule.Scheduler {
        final AtomicLong clock = new AtomicLong();
        final List<Task> tasks = new ArrayList<>();
        long sequence;

        @Override
        public MicrophoneTurnModule.Cancellation schedule(Runnable task, long delayNanos) {
            Task scheduled = new Task(clock.get() + Math.max(0, delayNanos), sequence++, task);
            tasks.add(scheduled);
            return () -> scheduled.cancelled = true;
        }

        void advanceBy(long nanos) {
            long target = clock.get() + nanos;
            while (true) {
                Task next = tasks.stream()
                        .filter(task -> !task.cancelled && !task.ran && task.due <= target)
                        .min(Comparator.comparingLong((Task task) -> task.due).thenComparingLong(task -> task.sequence))
                        .orElse(null);
                if (next == null) break;
                clock.set(next.due);
                next.ran = true;
                next.runnable.run();
            }
            clock.set(target);
            tasks.removeIf(task -> task.cancelled || task.ran);
        }

        boolean forceRunOneCancelledTask() {
            Task stale = tasks.stream().filter(task -> task.cancelled && !task.ran).findFirst().orElse(null);
            if (stale == null) return false;
            stale.ran = true;
            stale.runnable.run();
            return true;
        }

        int activeTasks() {
            int count = 0;
            for (Task task : tasks) if (!task.cancelled && !task.ran) count++;
            return count;
        }

        private static final class Task {
            final long due;
            final long sequence;
            final Runnable runnable;
            boolean cancelled;
            boolean ran;

            Task(long due, long sequence, Runnable runnable) {
                this.due = due;
                this.sequence = sequence;
                this.runnable = runnable;
            }
        }
    }
}
