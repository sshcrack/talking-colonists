package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

/** Immediate result of requesting a player-owned conversation. */
public record ConversationStartResult(@NotNull Status status, @NotNull String detail) {
    public enum Status {
        STARTED,
        PROVIDER_UNAVAILABLE,
        SLEEPING,
        VISITOR,
        IN_USE_BY_OTHER_PLAYER,
        ADDON_POLICY_VETO,
        CAPACITY_EXHAUSTED,
        FAILED
    }

    public boolean started() {
        return status == Status.STARTED;
    }

    public static ConversationStartResult startedResult() {
        return new ConversationStartResult(Status.STARTED, "conversation started");
    }

    public static ConversationStartResult rejected(Status status, String detail) {
        if (status == Status.STARTED) throw new IllegalArgumentException("Use startedResult() for STARTED");
        return new ConversationStartResult(status, detail == null ? status.name().toLowerCase() : detail);
    }
}
