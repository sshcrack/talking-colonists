package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Result of one addon-directed citizen line, completed after audible playback reaches a terminal state. */
public record AmbientLineResult(
        @NotNull Status status,
        @NotNull String transcript,
        @Nullable RejectionReason rejectionReason,
        @NotNull String detail
) {
    public enum Status { COMPLETED, REJECTED, FAILED, CANCELLED }

    public enum RejectionReason {
        PROVIDER_UNAVAILABLE,
        SLEEPING,
        VISITOR,
        COOLDOWN,
        BUSY,
        ADDON_POLICY_VETO,
        CAPACITY_EXHAUSTED
    }

    public boolean completed() {
        return status == Status.COMPLETED;
    }

    public static AmbientLineResult completed(String transcript) {
        return new AmbientLineResult(
                Status.COMPLETED,
                transcript == null ? "" : transcript,
                null,
                "audio playback completed"
        );
    }

    public static AmbientLineResult rejected(RejectionReason reason, String detail) {
        return new AmbientLineResult(
                Status.REJECTED,
                "",
                reason,
                detail == null ? reason.name().toLowerCase() : detail
        );
    }

    public static AmbientLineResult failed(String detail) {
        return new AmbientLineResult(Status.FAILED, "", null, detail == null ? "session failed" : detail);
    }

    public static AmbientLineResult cancelled() {
        return new AmbientLineResult(Status.CANCELLED, "", null, "session cancelled");
    }
}
