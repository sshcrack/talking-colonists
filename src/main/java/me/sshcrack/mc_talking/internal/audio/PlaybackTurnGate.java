package me.sshcrack.mc_talking.internal.audio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Small thread-safe ownership gate for one audible output turn.
 *
 * <p>Producer chunks are accepted only while their exact turn is active. Once a turn starts
 * draining, is cancelled, or completes, late chunks carrying that identity are rejected. The
 * callbacks execute while the gate is locked so cancellation cannot race an accepted enqueue.</p>
 */
public final class PlaybackTurnGate {
    public enum State { IDLE, ACTIVE, DRAINING, CANCELLED, COMPLETED }

    @Nullable private UUID turnId;
    private State state = State.IDLE;

    public synchronized void begin(@NotNull UUID id) {
        Objects.requireNonNull(id, "id");
        if (state == State.ACTIVE || state == State.DRAINING) {
            throw new IllegalStateException("Cannot replace an audible turn that is still " + state);
        }
        turnId = id;
        state = State.ACTIVE;
    }

    /** Runs {@code enqueue} iff {@code id} still owns the active producer turn. */
    public synchronized boolean accept(@NotNull UUID id, @NotNull Runnable enqueue) {
        Objects.requireNonNull(enqueue, "enqueue");
        if (!matches(id) || state != State.ACTIVE) return false;
        enqueue.run();
        return true;
    }

    /**
     * Closes the producer side and runs the final flush exactly once. New chunks are rejected from
     * this point onward, while already queued audio remains valid until playback drains.
     */
    public synchronized boolean beginDrain(@NotNull UUID id, @NotNull Runnable flush) {
        Objects.requireNonNull(flush, "flush");
        if (!matches(id)) return false;
        if (state == State.DRAINING) return true;
        if (state != State.ACTIVE) return false;
        state = State.DRAINING;
        flush.run();
        return true;
    }

    /** Cancels the exact turn and invokes the immediate stop/discard callback at most once. */
    public boolean cancel(@NotNull UUID id, @NotNull Runnable stopAndDiscard) {
        Objects.requireNonNull(stopAndDiscard, "stopAndDiscard");
        synchronized (this) {
            if (!matches(id) || state == State.CANCELLED || state == State.COMPLETED || state == State.IDLE) {
                return false;
            }
            // Revoke ownership while holding the same lock used by accept(). Once this lock is
            // released, no producer can enqueue another chunk for this turn. The potentially
            // re-entrant voice-chat stop itself deliberately runs outside the monitor.
            state = State.CANCELLED;
        }
        stopAndDiscard.run();
        return true;
    }

    /** Marks a naturally drained turn complete. */
    public synchronized boolean completeDrainedTurn() {
        if (state != State.DRAINING) return false;
        state = State.COMPLETED;
        return true;
    }

    public synchronized boolean isAccepting(@NotNull UUID id) {
        return matches(id) && state == State.ACTIVE;
    }

    public synchronized boolean owns(@NotNull UUID id) {
        return matches(id);
    }

    public synchronized @Nullable UUID turnId() {
        return turnId;
    }

    public synchronized @NotNull State state() {
        return state;
    }

    private boolean matches(UUID id) {
        return id != null && id.equals(turnId);
    }
}
