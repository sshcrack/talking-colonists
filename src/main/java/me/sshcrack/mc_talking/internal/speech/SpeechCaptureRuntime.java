package me.sshcrack.mc_talking.internal.speech;

import me.sshcrack.mc_talking.api.speech.PlayerSpeechCapture;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult.Status;
import me.sshcrack.mc_talking.internal.audio.MicrophoneTurnModule;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Player speech capture without a citizen (roadmap A10). One capture per player: microphone PCM
 * goes through a {@link MicrophoneTurnModule}, whose first closed turn marks the end of speech.
 * The captured audio is then handed to a {@link Transcriber} once and dropped.
 *
 * <p>Loader- and provider-independent: the backend supplies the transcriber, the gate (API key,
 * quota, voice chat connection), the indicator and the scheduler, so tests drive it with fake
 * audio and a manual clock.</p>
 */
public final class SpeechCaptureRuntime {
    /** Transcribes 48 kHz mono PCM. Completes with the text, or blank when nothing intelligible was said. */
    @FunctionalInterface
    public interface Transcriber {
        CompletableFuture<String> transcribe(short[] pcm);
    }

    /** Why a capture cannot start right now, or null when it can. */
    @FunctionalInterface
    public interface Gate {
        @Nullable Status blockedReason(UUID player);
    }

    /** Shows the player that the capture is listening, transcribing or finished. */
    public interface Indicator {
        void listening(UUID player);

        void transcribing(UUID player);

        void finished(UUID player);
    }

    /** Reported by the transcriber future when the provider rejected the request for quota. */
    public static final class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }

    public record Settings(long noSpeechTimeoutNanos, long speechEndGapNanos, int preRollFrames) {
        public static Settings defaults() {
            return new Settings(TimeUnit.SECONDS.toNanos(6), TimeUnit.MILLISECONDS.toNanos(900), 10);
        }
    }

    static final int SAMPLE_RATE = 48_000;

    private final Transcriber transcriber;
    private final Gate gate;
    private final Indicator indicator;
    private final MicrophoneTurnModule.Scheduler scheduler;
    private final LongSupplier nanoClock;
    private final Settings settings;
    private final Map<UUID, Capture> captures = new ConcurrentHashMap<>();

    public SpeechCaptureRuntime(Transcriber transcriber, Gate gate, Indicator indicator,
                                MicrophoneTurnModule.Scheduler scheduler, LongSupplier nanoClock, Settings settings) {
        this.transcriber = Objects.requireNonNull(transcriber, "transcriber");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.indicator = Objects.requireNonNull(indicator, "indicator");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Starts listening to the player for at most {@code maxDuration} (capped at {@link PlayerSpeechCapture#MAX_DURATION}). */
    public CompletableFuture<SpeechCaptureResult> start(UUID player, Duration maxDuration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxDuration.isNegative() || maxDuration.isZero()) {
            throw new IllegalArgumentException("maxDuration must be positive");
        }
        Status blocked = gate.blockedReason(player);
        if (blocked != null) {
            return CompletableFuture.completedFuture(SpeechCaptureResult.failure(blocked, Duration.ZERO, null));
        }
        Duration bounded = maxDuration.compareTo(PlayerSpeechCapture.MAX_DURATION) > 0
                ? PlayerSpeechCapture.MAX_DURATION : maxDuration;

        Capture capture = new Capture(player);
        if (captures.putIfAbsent(player, capture) != null) {
            return CompletableFuture.completedFuture(SpeechCaptureResult.failure(Status.BUSY, Duration.ZERO,
                    "A capture is already running for this player"));
        }
        capture.begin(bounded.toNanos());
        indicator.listening(player);
        return capture.result;
    }

    /**
     * Feeds one decoded microphone frame.
     *
     * @return whether a capture owns the player's microphone, so the frame must not reach a conversation
     */
    public boolean acceptMicrophone(UUID player, short[] pcm) {
        Capture capture = captures.get(player);
        if (capture == null) return false;
        capture.accept(pcm);
        return true;
    }

    public boolean isCapturing(UUID player) {
        return captures.containsKey(player);
    }

    /** Whether the capture is still listening (not yet transcribing). */
    public boolean isListening(UUID player) {
        Capture capture = captures.get(player);
        return capture != null && capture.isListening();
    }

    public boolean cancel(UUID player) {
        Capture capture = captures.get(player);
        return capture != null && capture.fail(Status.CANCELLED, "Cancelled");
    }

    public void playerLeft(UUID player) {
        Capture capture = captures.get(player);
        if (capture != null) capture.fail(Status.PLAYER_LEFT, "The player left");
    }

    /** Cancels every capture, e.g. when the server stops. */
    public void cancelAll() {
        for (Capture capture : captures.values()) {
            capture.fail(Status.CANCELLED, "The server is stopping");
        }
    }

    static Duration durationOf(int samples) {
        return Duration.ofNanos(samples * 1_000_000_000L / SAMPLE_RATE);
    }

    private enum Phase { LISTENING, TRANSCRIBING, DONE }

    private final class Capture implements MicrophoneTurnModule.ProviderAdapter {
        final UUID player;
        final UUID id = UUID.randomUUID();
        final CompletableFuture<SpeechCaptureResult> result = new CompletableFuture<>();
        final ArrayDeque<short[]> preRoll = new ArrayDeque<>();
        final ByteArrayOutputStream audio = new ByteArrayOutputStream();
        final MicrophoneTurnModule module;
        Phase phase = Phase.LISTENING;
        boolean speechStarted;
        int samples;
        @Nullable MicrophoneTurnModule.Cancellation noSpeechTimeout;
        @Nullable MicrophoneTurnModule.Cancellation maxDurationTimeout;

        Capture(UUID player) {
            this.player = player;
            MicrophoneTurnModule.Timing defaults = MicrophoneTurnModule.Timing.defaults();
            this.module = new MicrophoneTurnModule(id, nanoClock, scheduler, this, new MicrophoneTurnModule.Timing(
                    settings.speechEndGapNanos(),
                    defaults.paddingIntervalNanos(),
                    defaults.paddingDurationNanos(),
                    defaults.responseProgressTimeoutNanos(),
                    defaults.bargeInSpeechFrames(),
                    defaults.paddingFrameSamples()));
        }

        synchronized void begin(long maxDurationNanos) {
            noSpeechTimeout = scheduler.schedule(this::noSpeechTimedOut, settings.noSpeechTimeoutNanos());
            maxDurationTimeout = scheduler.schedule(this::maxDurationReached, maxDurationNanos);
        }

        synchronized boolean isListening() {
            return phase == Phase.LISTENING;
        }

        void accept(short[] pcm) {
            synchronized (this) {
                if (phase != Phase.LISTENING) return;
            }
            module.acceptMicrophone(pcm);
        }

        // --- MicrophoneTurnModule.ProviderAdapter ---

        @Override
        public void acceptAudio(UUID sessionId, @Nullable UUID turnId, MicrophoneTurnModule.InputKind kind, short[] pcm) {
            if (kind == MicrophoneTurnModule.InputKind.GENERATED_PADDING) return;
            synchronized (this) {
                if (phase != Phase.LISTENING) return;
                if (turnId == null) {
                    // Quiet audio before the first word: keep a little so the onset is not clipped.
                    preRoll.addLast(pcm.clone());
                    while (preRoll.size() > settings.preRollFrames()) preRoll.removeFirst();
                    return;
                }
                if (!speechStarted) {
                    speechStarted = true;
                    cancel(noSpeechTimeout);
                    noSpeechTimeout = null;
                    for (short[] frame : preRoll) append(frame);
                    preRoll.clear();
                }
                append(pcm);
            }
        }

        @Override
        public boolean cancelPlayback(UUID sessionId, UUID turnId) {
            return false;
        }

        @Override
        public void waitingForResponse(UUID sessionId, UUID turnId, boolean waiting) {
            // The module closes a turn after the speech-end gap: that is the end of the utterance.
            if (waiting) transcribe();
        }

        // --- lifecycle ---

        private void noSpeechTimedOut() {
            synchronized (this) {
                if (phase != Phase.LISTENING || speechStarted) return;
            }
            fail(Status.NO_SPEECH, "The player did not speak");
        }

        private void maxDurationReached() {
            boolean spoke;
            synchronized (this) {
                if (phase != Phase.LISTENING) return;
                spoke = speechStarted;
            }
            if (spoke) {
                transcribe();
            } else {
                fail(Status.NO_SPEECH, "The player did not speak");
            }
        }

        private void transcribe() {
            short[] pcm;
            synchronized (this) {
                if (phase != Phase.LISTENING || !speechStarted) return;
                phase = Phase.TRANSCRIBING;
                cancelTimeouts();
                pcm = toShorts(audio.toByteArray());
                audio.reset();
            }
            module.close();
            indicator.transcribing(player);
            Duration speech = durationOf(pcm.length);

            CompletableFuture<String> request;
            try {
                request = transcriber.transcribe(pcm);
            } catch (RuntimeException error) {
                request = CompletableFuture.failedFuture(error);
            }
            request.whenComplete((text, error) -> {
                if (error != null) {
                    Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null
                            ? error.getCause() : error;
                    Status status = cause instanceof QuotaExceededException ? Status.QUOTA : Status.PROVIDER_ERROR;
                    complete(SpeechCaptureResult.failure(status, speech, cause.getMessage()));
                } else if (text == null || text.isBlank()) {
                    complete(SpeechCaptureResult.failure(Status.NO_SPEECH, speech, "No intelligible speech"));
                } else {
                    complete(SpeechCaptureResult.transcribed(text.strip(), speech));
                }
            });
        }

        /** Ends the capture with a failure. Works while listening or transcribing. */
        boolean fail(Status status, String detail) {
            Duration speech;
            synchronized (this) {
                if (phase == Phase.DONE) return false;
                speech = durationOf(samples);
                cancelTimeouts();
                audio.reset();
                preRoll.clear();
            }
            module.close();
            return complete(SpeechCaptureResult.failure(status, speech, detail));
        }

        private boolean complete(SpeechCaptureResult outcome) {
            synchronized (this) {
                if (phase == Phase.DONE) return false;
                phase = Phase.DONE;
            }
            captures.remove(player, this);
            indicator.finished(player);
            result.complete(outcome);
            return true;
        }

        private void append(short[] pcm) {
            for (short sample : pcm) {
                audio.write(sample & 0xFF);
                audio.write((sample >> 8) & 0xFF);
            }
            samples += pcm.length;
        }

        private void cancelTimeouts() {
            cancel(noSpeechTimeout);
            cancel(maxDurationTimeout);
            noSpeechTimeout = null;
            maxDurationTimeout = null;
        }
    }

    private static void cancel(@Nullable MicrophoneTurnModule.Cancellation cancellation) {
        if (cancellation != null) cancellation.cancel();
    }

    private static short[] toShorts(byte[] littleEndian) {
        short[] pcm = new short[littleEndian.length / 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) ((littleEndian[2 * i] & 0xFF) | (littleEndian[2 * i + 1] << 8));
        }
        return pcm;
    }

    /** Whole seconds of listening left before {@code deadlineNanos}, rounded up and never below zero. */
    public static long secondsLeft(long deadlineNanos, long nowNanos) {
        long left = deadlineNanos - nowNanos;
        return left <= 0 ? 0 : (left + 999_999_999L) / 1_000_000_000L;
    }

}
