package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

/** Result of one addon-directed citizen line, completed after audible playback ends. */
public record AmbientLineResult(@NotNull Status status, @NotNull String transcript, @NotNull String detail) {
    public enum Status { COMPLETED, FAILED, CANCELLED }

    public static AmbientLineResult completed(String transcript) {
        return new AmbientLineResult(Status.COMPLETED, transcript == null ? "" : transcript, "audio playback completed");
    }

    public static AmbientLineResult failed(String detail) {
        return new AmbientLineResult(Status.FAILED, "", detail == null ? "session failed" : detail);
    }

    public static AmbientLineResult cancelled() {
        return new AmbientLineResult(Status.CANCELLED, "", "session cancelled");
    }
}
