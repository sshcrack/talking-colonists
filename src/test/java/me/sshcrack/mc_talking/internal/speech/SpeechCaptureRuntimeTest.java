package me.sshcrack.mc_talking.internal.speech;

import me.sshcrack.mc_talking.api.speech.PlayerSpeechCapture;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult.Status;
import me.sshcrack.mc_talking.internal.audio.MicrophoneTurnModule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechCaptureRuntimeTest {
    private static final long FRAME = TimeUnit.MILLISECONDS.toNanos(20);
    private static final Duration TEN_SECONDS = Duration.ofSeconds(10);

    private final UUID player = UUID.randomUUID();
    private final ManualScheduler scheduler = new ManualScheduler();
    private final FakeTranscriber transcriber = new FakeTranscriber();
    private final FakeIndicator indicator = new FakeIndicator();
    private Status blocked;
    private final SpeechCaptureRuntime runtime = new SpeechCaptureRuntime(transcriber, ignored -> blocked, indicator,
            scheduler, scheduler.clock::get, SpeechCaptureRuntime.Settings.defaults());

    @Test
    void speechIsTranscribedAfterThePlayerStopsTalking() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        quiet(5);
        speak(50);
        quiet(10);
        assertFalse(result.isDone(), "the speech-end gap has not passed yet");
        assertTrue(transcriber.requests.isEmpty());

        quiet(60);
        assertEquals(1, transcriber.requests.size());
        short[] sent = transcriber.requests.get(0);
        assertTrue(sent.length >= 50 * 960, "all speech frames are sent");
        assertTrue(sent.length <= (5 + 50 + 60) * 960, "trailing audio after the end is bounded");
        assertEquals(List.of("listening", "transcribing"), indicator.events);

        transcriber.pending.complete("  bring me twenty logs  ");
        SpeechCaptureResult outcome = result.join();
        assertEquals(Status.TRANSCRIBED, outcome.status());
        assertEquals("bring me twenty logs", outcome.transcript());
        assertTrue(outcome.speech().toMillis() >= 1000);
        assertEquals(List.of("listening", "transcribing", "finished"), indicator.events);
        assertFalse(runtime.isCapturing(player));
    }

    @Test
    void silenceTimesOutWithoutARequest() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        quiet(250);
        scheduler.advanceBy(TimeUnit.SECONDS.toNanos(1));

        assertEquals(Status.NO_SPEECH, result.join().status());
        assertTrue(transcriber.requests.isEmpty());
        assertEquals(Duration.ZERO, result.join().speech());
        assertFalse(runtime.isCapturing(player));
    }

    @Test
    void maxDurationEndsAnOngoingUtterance() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, Duration.ofSeconds(2));
        speak(150);

        assertEquals(1, transcriber.requests.size(), "reaching the limit transcribes what was said");
        assertTrue(transcriber.requests.get(0).length <= 101 * 960, "no audio after the limit");
        transcriber.pending.complete("a long speech");
        assertEquals("a long speech", result.join().transcript());
    }

    @Test
    void maxDurationIsCapped() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, Duration.ofMinutes(5));
        long frames = PlayerSpeechCapture.MAX_DURATION.toNanos() / FRAME;
        for (long i = 0; i < frames + 10 && transcriber.requests.isEmpty(); i++) speak(1);

        assertEquals(1, transcriber.requests.size());
        assertTrue(transcriber.requests.get(0).length <= (frames + 1) * 960);
        assertFalse(result.isDone());
    }

    @Test
    void cancellationEndsTheCapture() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        speak(10);

        assertTrue(runtime.cancel(player));
        assertEquals(Status.CANCELLED, result.join().status());
        assertFalse(runtime.cancel(player), "nothing left to cancel");
        assertFalse(runtime.acceptMicrophone(player, speech()), "the microphone is released");
        scheduler.advanceBy(TimeUnit.SECONDS.toNanos(20));
        assertTrue(transcriber.requests.isEmpty());
        assertEquals(List.of("listening", "finished"), indicator.events);
    }

    @Test
    void cancellationWhileTranscribingWins() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        speak(20);
        quiet(60);
        assertEquals(1, transcriber.requests.size());

        runtime.cancel(player);
        transcriber.pending.complete("too late");
        assertEquals(Status.CANCELLED, result.join().status());
    }

    @Test
    void playerDisconnectEndsTheCapture() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        speak(10);

        runtime.playerLeft(player);
        assertEquals(Status.PLAYER_LEFT, result.join().status());
        assertTrue(result.join().speech().toMillis() > 0);
        assertFalse(runtime.isCapturing(player));
    }

    @Test
    void secondCaptureForTheSamePlayerIsBusy() {
        runtime.start(player, TEN_SECONDS);
        assertEquals(Status.BUSY, runtime.start(player, TEN_SECONDS).join().status());
        assertTrue(runtime.isCapturing(player));
    }

    @Test
    void blockedCapturesNeverListen() {
        blocked = Status.NO_VOICE_CHAT;
        assertEquals(Status.NO_VOICE_CHAT, runtime.start(player, TEN_SECONDS).join().status());
        assertFalse(runtime.isCapturing(player));
        assertFalse(runtime.acceptMicrophone(player, speech()));
        assertTrue(indicator.events.isEmpty());
    }

    @Test
    void quotaErrorsAreTyped() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        speak(20);
        quiet(60);
        transcriber.pending.completeExceptionally(new SpeechCaptureRuntime.QuotaExceededException("quota"));

        assertEquals(Status.QUOTA, result.join().status());
    }

    @Test
    void providerErrorsAreTyped() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        speak(20);
        quiet(60);
        transcriber.pending.completeExceptionally(new IllegalStateException("HTTP 500"));

        SpeechCaptureResult outcome = result.join();
        assertEquals(Status.PROVIDER_ERROR, outcome.status());
        assertNull(outcome.transcript());
    }

    @Test
    void blankTranscriptIsNoSpeech() {
        CompletableFuture<SpeechCaptureResult> result = runtime.start(player, TEN_SECONDS);
        speak(20);
        quiet(60);
        transcriber.pending.complete(" ");

        assertEquals(Status.NO_SPEECH, result.join().status());
    }

    @Test
    void framesWithoutACaptureAreNotConsumed() {
        assertFalse(runtime.acceptMicrophone(player, speech()));
    }

    @Test
    void cancelAllStopsEveryCapture() {
        UUID other = UUID.randomUUID();
        CompletableFuture<SpeechCaptureResult> first = runtime.start(player, TEN_SECONDS);
        CompletableFuture<SpeechCaptureResult> second = runtime.start(other, TEN_SECONDS);

        runtime.cancelAll();
        assertEquals(Status.CANCELLED, first.join().status());
        assertEquals(Status.CANCELLED, second.join().status());
    }

    @Test
    void invalidDurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> runtime.start(player, Duration.ZERO));
    }

    private void speak(int frames) {
        for (int i = 0; i < frames; i++) {
            runtime.acceptMicrophone(player, speech());
            scheduler.advanceBy(FRAME);
        }
    }

    private void quiet(int frames) {
        for (int i = 0; i < frames; i++) {
            runtime.acceptMicrophone(player, new short[960]);
            scheduler.advanceBy(FRAME);
        }
    }

    private static short[] speech() {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (i % 2 == 0 ? 2200 : -2200);
        return pcm;
    }

    private static final class FakeTranscriber implements SpeechCaptureRuntime.Transcriber {
        final List<short[]> requests = new ArrayList<>();
        CompletableFuture<String> pending;

        @Override
        public CompletableFuture<String> transcribe(short[] pcm) {
            requests.add(pcm);
            pending = new CompletableFuture<>();
            return pending;
        }
    }

    private static final class FakeIndicator implements SpeechCaptureRuntime.Indicator {
        final List<String> events = new ArrayList<>();

        @Override
        public void listening(UUID player) {
            events.add("listening");
        }

        @Override
        public void transcribing(UUID player) {
            events.add("transcribing");
        }

        @Override
        public void finished(UUID player) {
            events.add("finished");
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

    @Test
    void countdownRoundsUpAndStopsAtZero() {
        long second = 1_000_000_000L;
        assertEquals(30, SpeechCaptureRuntime.secondsLeft(30 * second, 0));
        assertEquals(25, SpeechCaptureRuntime.secondsLeft(30 * second, 5 * second + 1));
        assertEquals(1, SpeechCaptureRuntime.secondsLeft(30 * second, 30 * second - 1));
        assertEquals(0, SpeechCaptureRuntime.secondsLeft(30 * second, 31 * second));
    }

}
