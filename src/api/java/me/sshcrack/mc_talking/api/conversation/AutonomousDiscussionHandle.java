package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Caller-owned delegation of a controlled session's floor to core automatic speaker selection.
 *
 * <p>Calls may originate on any thread. State transitions and completion are delivered from the
 * same Minecraft server-thread executor as controlled turn completions. Pausing does not cut off an
 * already audible turn; it only prevents another automatic turn from starting. Stopping relinquishes
 * automatic ownership immediately and interrupts an automatic turn if one is active.</p>
 */
public interface AutonomousDiscussionHandle extends AutoCloseable {
    enum State { RUNNING, PAUSED, COMPLETED, STOPPED }

    enum PauseReason {
        CALLER,
        PLAYER_INTERRUPTED,
        CAPACITY_UNAVAILABLE
    }

    enum CompletionReason {
        TURN_LIMIT,
        DURATION_LIMIT,
        NO_AVAILABLE_PARTICIPANTS,
        PROVIDER_FAILURE,
        STOPPED
    }

    @NotNull State state();

    int completedTurns();

    /** Present only while paused. */
    @NotNull Optional<PauseReason> pauseReason();

    /** Prevents another automatic turn from starting. The current audible turn may finish. */
    void pause();

    /** Resumes fair automatic selection once no manual/current turn owns the floor. */
    void resume();

    /** Permanently relinquishes automatic floor ownership. Safe to call repeatedly. */
    void stop();

    /** Completes exactly once on a terminal automatic-discussion state. */
    @NotNull CompletableFuture<CompletionReason> completion();

    @Override
    default void close() {
        stop();
    }
}
