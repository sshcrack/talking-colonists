package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/** One speaker-attributed entry in a controlled conversation's bounded transcript. */
public record ConversationTranscriptEntry(
        @NotNull SpeakerKind speakerKind,
        @NotNull UUID speakerId,
        @NotNull String speakerName,
        @NotNull String text,
        long gameTimeTicks
) {
    public ConversationTranscriptEntry {
        Objects.requireNonNull(speakerKind, "speakerKind");
        Objects.requireNonNull(speakerId, "speakerId");
        Objects.requireNonNull(speakerName, "speakerName");
        Objects.requireNonNull(text, "text");
        if (speakerName.isBlank()) throw new IllegalArgumentException("speakerName must not be blank");
        if (text.isBlank()) throw new IllegalArgumentException("text must not be blank");
    }

    public enum SpeakerKind {
        CITIZEN,
        PLAYER
    }
}
