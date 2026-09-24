package me.sshcrack.mc_talking.api.colony;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An event an addon records in a colony's event feed, e.g. {@code ("elections", "election_won",
 * "Maria Silva won the mayoral election")}. Citizens mention recent events in conversation, so write
 * {@code description} as a short, self-contained sentence.
 */
public record AddonColonyEvent(@NotNull String namespace, @NotNull String key, @NotNull String description) {
    public static final int MAX_DESCRIPTION_LENGTH = 300;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");

    public AddonColonyEvent {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(description, "description");
        if (!ID.matcher(namespace).matches()) throw new IllegalArgumentException("namespace must match " + ID.pattern());
        if (!ID.matcher(key).matches()) throw new IllegalArgumentException("key must match " + ID.pattern());
        description = description.strip();
        if (description.isEmpty()) throw new IllegalArgumentException("description must not be blank");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description exceeds " + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }
}
