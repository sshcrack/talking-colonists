package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Outcome of {@link CitizenConversationService#sendPlayerText} and
 * {@link CitizenConversationService#addContext} (API 2.1, roadmap A4).
 *
 * @param status what happened
 * @param detail human-readable reason when not delivered
 */
public record PlayerTextResult(@NotNull Status status, @Nullable String detail) {
    /** Longest typed line or context note, in characters, after trimming. */
    public static final int MAX_CHARS = 500;

    public enum Status {
        /** Sent to the citizen's session (queued if it is still connecting). */
        DELIVERED,
        /** The player is not in a direct conversation with this citizen. */
        NOT_IN_CONVERSATION,
        /** The text was empty after trimming. */
        EMPTY,
        /** The text is longer than {@link #MAX_CHARS}. */
        TOO_LONG,
        /** The player sent too many lines or notes in a short time; retry in a few seconds. */
        RATE_LIMITED
    }

    public PlayerTextResult {
        Objects.requireNonNull(status, "status");
    }

    public static @NotNull PlayerTextResult delivered() {
        return new PlayerTextResult(Status.DELIVERED, null);
    }

    public static @NotNull PlayerTextResult rejected(@NotNull Status status, @Nullable String detail) {
        if (status == Status.DELIVERED) throw new IllegalArgumentException("use delivered()");
        return new PlayerTextResult(status, detail);
    }

    public boolean isDelivered() {
        return status == Status.DELIVERED;
    }
}
