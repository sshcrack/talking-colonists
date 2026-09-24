package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Thrown by {@code createControlledSession} when the attendees cannot share one session. It extends
 * {@link IllegalArgumentException}, so callers that already catch that keep working.
 */
public class ControlledSessionRejectedException extends IllegalArgumentException {
    public enum Reason {
        /** Attendees are in different dimensions, so they cannot hear each other. */
        CROSS_DIMENSION
    }

    private final Reason reason;

    public ControlledSessionRejectedException(@NotNull Reason reason, @NotNull String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public @NotNull Reason reason() {
        return reason;
    }
}
