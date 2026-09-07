package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Terminal result for exactly one controlled floor turn. */
public record ControlledTurnResult(
        @NotNull UUID sessionId,
        @NotNull UUID turnId,
        @NotNull Status status,
        @Nullable FailureReason failureReason,
        @NotNull String transcript,
        @NotNull String detail
) {
    public enum Status { COMPLETED, REJECTED, FAILED, INTERRUPTED, SESSION_ENDED }

    public enum FailureReason {
        SPEAKER_NOT_PARTICIPANT,
        SESSION_CLOSED,
        TURN_ALREADY_ACTIVE,
        UNSUPPORTED_OPERATION,
        PROVIDER_UNAVAILABLE,
        SPEAKER_UNAVAILABLE,
        CAPACITY_EXHAUSTED,
        SPEAKER_UNLOADED,
        PROVIDER_FAILED,
        INTERNAL_FAILURE
    }

    public boolean completed() { return status == Status.COMPLETED; }

    public static @NotNull ControlledTurnResult completed(UUID sessionId, UUID turnId, String transcript) {
        return new ControlledTurnResult(sessionId, turnId, Status.COMPLETED, null,
                transcript == null ? "" : transcript, "audible playback completed");
    }

    public static @NotNull ControlledTurnResult rejected(UUID sessionId, UUID turnId, FailureReason reason, String detail) {
        return new ControlledTurnResult(sessionId, turnId, Status.REJECTED, reason, "", detail);
    }

    public static @NotNull ControlledTurnResult failed(UUID sessionId, UUID turnId, FailureReason reason, String detail) {
        return new ControlledTurnResult(sessionId, turnId, Status.FAILED, reason, "", detail);
    }

    public static @NotNull ControlledTurnResult interrupted(UUID sessionId, UUID turnId) {
        return new ControlledTurnResult(sessionId, turnId, Status.INTERRUPTED, null, "", "turn interrupted");
    }

    public static @NotNull ControlledTurnResult sessionEnded(UUID sessionId, UUID turnId, String detail) {
        return new ControlledTurnResult(sessionId, turnId, Status.SESSION_ENDED, FailureReason.SESSION_CLOSED, "", detail);
    }
}
