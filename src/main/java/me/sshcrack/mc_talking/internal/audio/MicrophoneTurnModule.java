package me.sshcrack.mc_talking.internal.audio;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Owns one provider session's local microphone-turn policy and all delayed work derived from it.
 *
 * <p>The module deliberately does not serialize Gemini protocol messages. Callers provide a
 * {@link ProviderAdapter}; this class decides only whether decoded microphone input is speech-like,
 * when local playback may be interrupted, and how long generated trailing silence remains useful.
 * Every delayed callback captures both the immutable provider-session identity and the exact local
 * microphone-turn identity before touching state.</p>
 */
public final class MicrophoneTurnModule implements AutoCloseable {
    public enum InputKind {
        SPEECH,
        QUIET_MICROPHONE,
        GENERATED_PADDING
    }

    public enum ProviderProgress {
        INPUT_OBSERVED,
        RESPONSE_STARTED,
        TURN_COMPLETED
    }

    public enum DiagnosticEvent {
        TURN_STARTED,
        INPUT_ACCEPTED,
        PLAYBACK_CANCELLED,
        TURN_CLOSED,
        PADDING_STARTED,
        PADDING_STOPPED,
        PROVIDER_INPUT_OBSERVED,
        PROVIDER_RESPONSE,
        RESPONSE_TIMEOUT,
        PROVIDER_DISCONNECTED,
        MODULE_CLOSED
    }

    public record Diagnostic(
            UUID sessionId,
            @Nullable UUID turnId,
            DiagnosticEvent event,
            @Nullable InputKind inputKind
    ) {
    }

    @FunctionalInterface
    public interface Scheduler {
        Cancellation schedule(Runnable task, long delayNanos);
    }

    @FunctionalInterface
    public interface Cancellation {
        void cancel();
    }

    /** Adapter at the provider/playback seam. No raw microphone contents are exposed to diagnostics. */
    public interface ProviderAdapter {
        void acceptAudio(UUID sessionId, @Nullable UUID turnId, InputKind kind, short[] pcm);

        boolean cancelPlayback(UUID sessionId, UUID turnId);

        void waitingForResponse(UUID sessionId, UUID turnId, boolean waiting);

        default void diagnostic(Diagnostic diagnostic) {
        }
    }

    public record Timing(
            long speechEndGapNanos,
            long paddingIntervalNanos,
            long paddingDurationNanos,
            long responseProgressTimeoutNanos,
            int bargeInSpeechFrames,
            int paddingFrameSamples
    ) {
        public Timing {
            if (speechEndGapNanos <= 0) throw new IllegalArgumentException("speechEndGapNanos must be positive");
            if (paddingIntervalNanos <= 0) throw new IllegalArgumentException("paddingIntervalNanos must be positive");
            if (paddingDurationNanos <= 0) throw new IllegalArgumentException("paddingDurationNanos must be positive");
            if (responseProgressTimeoutNanos <= 0) {
                throw new IllegalArgumentException("responseProgressTimeoutNanos must be positive");
            }
            if (bargeInSpeechFrames <= 0) throw new IllegalArgumentException("bargeInSpeechFrames must be positive");
            if (paddingFrameSamples <= 0) throw new IllegalArgumentException("paddingFrameSamples must be positive");
        }

        public static Timing defaults() {
            return new Timing(
                    TimeUnit.MILLISECONDS.toNanos(350),
                    TimeUnit.MILLISECONDS.toNanos(20),
                    TimeUnit.SECONDS.toNanos(2),
                    TimeUnit.SECONDS.toNanos(10),
                    2,
                    960
            );
        }
    }

    private final UUID sessionId;
    private final LongSupplier nanoClock;
    private final Scheduler scheduler;
    private final ProviderAdapter provider;
    private final Timing timing;

    @Nullable
    private Turn currentTurn;
    private boolean closed;

    public MicrophoneTurnModule(
            UUID sessionId,
            LongSupplier nanoClock,
            Scheduler scheduler,
            ProviderAdapter provider
    ) {
        this(sessionId, nanoClock, scheduler, provider, Timing.defaults());
    }

    public MicrophoneTurnModule(
            UUID sessionId,
            LongSupplier nanoClock,
            Scheduler scheduler,
            ProviderAdapter provider,
            Timing timing
    ) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.timing = Objects.requireNonNull(timing, "timing");
    }

    /**
     * Accepts one decoded real microphone packet. Quiet packets are still forwarded to Gemini's
     * automatic VAD but can never trigger local playback cancellation.
     *
     * @return {@code true} when the decoded packet qualified as a local speech candidate
     */
    public boolean acceptMicrophone(short[] pcm) {
        Objects.requireNonNull(pcm, "pcm");
        boolean speech = PcmSpeechDetector.isSpeechCandidate(pcm);
        UUID turnId = null;
        boolean started = false;
        boolean cancelPlayback = false;

        synchronized (this) {
            if (closed) return speech;
            if (speech) {
                Turn turn = currentTurn;
                if (turn == null || turn.closed) {
                    if (turn != null) {
                        cancelOwnedWorkLocked(turn);
                        if (turn.waitingForResponse) {
                            provider.waitingForResponse(sessionId, turn.turnId, false);
                        }
                    }
                    turn = new Turn(UUID.randomUUID());
                    currentTurn = turn;
                    started = true;
                }

                turnId = turn.turnId;
                turn.consecutiveSpeechFrames++;
                cancel(turn.speechEndCancellation);
                UUID scheduledTurnId = turn.turnId;
                turn.speechEndCancellation = scheduler.schedule(
                        () -> closeTurnAfterSilence(sessionId, scheduledTurnId),
                        timing.speechEndGapNanos()
                );

                if (!turn.playbackCancelled && turn.consecutiveSpeechFrames >= timing.bargeInSpeechFrames()) {
                    cancelPlayback = true;
                }
            } else {
                Turn turn = currentTurn;
                if (turn != null && !turn.closed) {
                    turn.consecutiveSpeechFrames = 0;
                    turnId = turn.turnId;
                }
            }
        }

        if (started) diagnostic(turnId, DiagnosticEvent.TURN_STARTED, InputKind.SPEECH);
        if (cancelPlayback && turnId != null && provider.cancelPlayback(sessionId, turnId)) {
            synchronized (this) {
                Turn turn = currentTurn;
                if (turn != null && turn.turnId.equals(turnId)) turn.playbackCancelled = true;
            }
            diagnostic(turnId, DiagnosticEvent.PLAYBACK_CANCELLED, InputKind.SPEECH);
        }

        InputKind kind = speech ? InputKind.SPEECH : InputKind.QUIET_MICROPHONE;
        provider.acceptAudio(sessionId, turnId, kind, pcm);
        diagnostic(turnId, DiagnosticEvent.INPUT_ACCEPTED, kind);
        return speech;
    }

    /** Records provider-side progress without changing Gemini's configured VAD mode. */
    public void providerProgress(ProviderProgress progress) {
        Objects.requireNonNull(progress, "progress");
        if (progress == ProviderProgress.INPUT_OBSERVED) {
            UUID turnId;
            synchronized (this) {
                if (closed) return;
                turnId = currentTurn == null ? null : currentTurn.turnId;
            }
            diagnostic(turnId, DiagnosticEvent.PROVIDER_INPUT_OBSERVED, null);
            return;
        }

        Turn completed;
        synchronized (this) {
            if (closed || currentTurn == null) return;
            completed = currentTurn;
            cancelOwnedWorkLocked(completed);
            if (completed.waitingForResponse) {
                provider.waitingForResponse(sessionId, completed.turnId, false);
            }
            currentTurn = null;
        }
        diagnostic(completed.turnId, DiagnosticEvent.PROVIDER_RESPONSE, null);
    }

    /** Cancels all turn-owned delayed work when the provider transport leaves the active state. */
    public void providerDisconnected() {
        Turn abandoned;
        synchronized (this) {
            if (closed) return;
            abandoned = currentTurn;
            if (abandoned != null) {
                cancelOwnedWorkLocked(abandoned);
                if (abandoned.waitingForResponse) {
                    provider.waitingForResponse(sessionId, abandoned.turnId, false);
                }
            }
            currentTurn = null;
        }
        diagnostic(abandoned == null ? null : abandoned.turnId, DiagnosticEvent.PROVIDER_DISCONNECTED, null);
    }

    @Override
    public void close() {
        Turn abandoned;
        synchronized (this) {
            if (closed) return;
            closed = true;
            abandoned = currentTurn;
            if (abandoned != null) {
                cancelOwnedWorkLocked(abandoned);
                if (abandoned.waitingForResponse) {
                    provider.waitingForResponse(sessionId, abandoned.turnId, false);
                }
            }
            currentTurn = null;
        }
        diagnostic(abandoned == null ? null : abandoned.turnId, DiagnosticEvent.MODULE_CLOSED, null);
    }

    synchronized boolean hasOwnedScheduledWork() {
        Turn turn = currentTurn;
        return turn != null && (turn.speechEndCancellation != null
                || turn.paddingTickCancellation != null
                || turn.paddingStopCancellation != null
                || turn.responseTimeoutCancellation != null);
    }

    @Nullable
    synchronized UUID currentTurnId() {
        return currentTurn == null ? null : currentTurn.turnId;
    }

    private void closeTurnAfterSilence(UUID expectedSessionId, UUID expectedTurnId) {
        Turn turn;
        synchronized (this) {
            turn = ownedTurnLocked(expectedSessionId, expectedTurnId);
            if (turn == null || turn.closed) return;
            turn.closed = true;
            turn.speechEndCancellation = null;
            turn.consecutiveSpeechFrames = 0;
            turn.waitingForResponse = true;
            turn.paddingEndsAtNanos = saturatedAdd(nanoClock.getAsLong(), timing.paddingDurationNanos());
            turn.paddingTickCancellation = scheduler.schedule(
                    () -> sendPadding(expectedSessionId, expectedTurnId),
                    0L
            );
            turn.paddingStopCancellation = scheduler.schedule(
                    () -> stopPadding(expectedSessionId, expectedTurnId),
                    timing.paddingDurationNanos()
            );
            turn.responseTimeoutCancellation = scheduler.schedule(
                    () -> responseTimedOut(expectedSessionId, expectedTurnId),
                    timing.responseProgressTimeoutNanos()
            );
            provider.waitingForResponse(sessionId, expectedTurnId, true);
        }

        diagnostic(expectedTurnId, DiagnosticEvent.TURN_CLOSED, null);
        diagnostic(expectedTurnId, DiagnosticEvent.PADDING_STARTED, InputKind.GENERATED_PADDING);
    }

    private void sendPadding(UUID expectedSessionId, UUID expectedTurnId) {
        boolean accepted = false;
        synchronized (this) {
            Turn turn = ownedTurnLocked(expectedSessionId, expectedTurnId);
            if (turn == null || !turn.closed || nanoClock.getAsLong() >= turn.paddingEndsAtNanos) return;
            turn.paddingTickCancellation = null;
            short[] padding = new short[timing.paddingFrameSamples()];
            provider.acceptAudio(sessionId, expectedTurnId, InputKind.GENERATED_PADDING, padding);
            accepted = true;
            if (nanoClock.getAsLong() < turn.paddingEndsAtNanos) {
                turn.paddingTickCancellation = scheduler.schedule(
                        () -> sendPadding(expectedSessionId, expectedTurnId),
                        timing.paddingIntervalNanos()
                );
            }
        }
        if (accepted) diagnostic(expectedTurnId, DiagnosticEvent.INPUT_ACCEPTED, InputKind.GENERATED_PADDING);
    }

    private void stopPadding(UUID expectedSessionId, UUID expectedTurnId) {
        boolean stopped = false;
        synchronized (this) {
            Turn turn = ownedTurnLocked(expectedSessionId, expectedTurnId);
            if (turn == null || !turn.closed) return;
            turn.paddingStopCancellation = null;
            cancel(turn.paddingTickCancellation);
            turn.paddingTickCancellation = null;
            stopped = true;
        }
        if (stopped) diagnostic(expectedTurnId, DiagnosticEvent.PADDING_STOPPED, InputKind.GENERATED_PADDING);
    }

    private void responseTimedOut(UUID expectedSessionId, UUID expectedTurnId) {
        Turn timedOut;
        synchronized (this) {
            timedOut = ownedTurnLocked(expectedSessionId, expectedTurnId);
            if (timedOut == null || !timedOut.closed) return;
            timedOut.responseTimeoutCancellation = null;
            cancelOwnedWorkLocked(timedOut);
            if (timedOut.waitingForResponse) {
                provider.waitingForResponse(sessionId, timedOut.turnId, false);
            }
            currentTurn = null;
        }
        diagnostic(timedOut.turnId, DiagnosticEvent.RESPONSE_TIMEOUT, null);
    }

    @Nullable
    private Turn ownedTurnLocked(UUID expectedSessionId, UUID expectedTurnId) {
        if (closed || !sessionId.equals(expectedSessionId)) return null;
        Turn turn = currentTurn;
        return turn != null && turn.turnId.equals(expectedTurnId) ? turn : null;
    }

    private void cancelOwnedWorkLocked(Turn turn) {
        cancel(turn.speechEndCancellation);
        cancel(turn.paddingTickCancellation);
        cancel(turn.paddingStopCancellation);
        cancel(turn.responseTimeoutCancellation);
        turn.speechEndCancellation = null;
        turn.paddingTickCancellation = null;
        turn.paddingStopCancellation = null;
        turn.responseTimeoutCancellation = null;
    }

    private void diagnostic(@Nullable UUID turnId, DiagnosticEvent event, @Nullable InputKind kind) {
        provider.diagnostic(new Diagnostic(sessionId, turnId, event, kind));
    }

    private static void cancel(@Nullable Cancellation cancellation) {
        if (cancellation != null) cancellation.cancel();
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static final class Turn {
        final UUID turnId;
        long paddingEndsAtNanos;
        int consecutiveSpeechFrames;
        boolean closed;
        boolean playbackCancelled;
        boolean waitingForResponse;
        @Nullable Cancellation speechEndCancellation;
        @Nullable Cancellation paddingTickCancellation;
        @Nullable Cancellation paddingStopCancellation;
        @Nullable Cancellation responseTimeoutCancellation;

        Turn(UUID turnId) {
            this.turnId = turnId;
        }
    }
}
