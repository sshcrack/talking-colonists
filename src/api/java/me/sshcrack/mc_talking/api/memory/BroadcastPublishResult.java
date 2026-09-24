package me.sshcrack.mc_talking.api.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Outcome of {@link CitizenMemoryService#publishBroadcast}.
 *
 * @param status      what happened
 * @param broadcastId id to pass to {@link CitizenMemoryService#retractBroadcast}; set only when published
 * @param recipients  citizens that learned the broadcast immediately (the rest hear it through propagation)
 */
public record BroadcastPublishResult(@NotNull Status status, @Nullable String broadcastId, int recipients) {
    public enum Status {
        PUBLISHED,
        /** The colony published too many broadcasts recently. Try again later. */
        RATE_LIMITED,
        /** The server disabled broadcasts in the Talking Colonists config. */
        DISABLED,
        /** No citizen could receive it: the origin citizen is unknown or no citizen is near the origin position. */
        NO_RECIPIENTS,
        /** The colony or its server is not available. */
        UNAVAILABLE
    }

    public BroadcastPublishResult {
        Objects.requireNonNull(status, "status");
        if ((status == Status.PUBLISHED) != (broadcastId != null)) {
            throw new IllegalArgumentException("broadcastId is set exactly when the status is PUBLISHED");
        }
    }

    public static @NotNull BroadcastPublishResult published(@NotNull String broadcastId, int recipients) {
        return new BroadcastPublishResult(Status.PUBLISHED, broadcastId, recipients);
    }

    public static @NotNull BroadcastPublishResult failed(@NotNull Status status) {
        return new BroadcastPublishResult(status, null, 0);
    }

    public boolean isPublished() {
        return status == Status.PUBLISHED;
    }
}
