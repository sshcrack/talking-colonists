package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * One finished utterance in a core-managed conversation (API 2.1,
 * {@link me.sshcrack.mc_talking.api.ApiFeature#UTTERANCE_EVENTS}).
 *
 * <p>Transcription is best effort: speech-to-text can mishear, and a citizen's text is what the
 * model said, not a verified fact. Do not treat it as proof for irreversible gameplay; confirm such
 * actions through your own AI tool instead.</p>
 *
 * @param kind        the conversation kind
 * @param citizen     the citizen speaking, or the citizen spoken to; null for a player statement
 *                    addressed to a whole controlled session
 * @param speaker     who spoke
 * @param speakerId   the speaking player's or citizen's UUID; null for system notes
 * @param speakerName display name of the speaker
 * @param text        the final, whole utterance (never a partial transcription chunk)
 * @param sessionId   controlled session ID, if any
 * @param turnId      controlled turn ID, if any
 * @param source      where the text came from
 */
public record ConversationUtteranceEvent(
        @NotNull ConversationKind kind,
        @Nullable AbstractEntityCitizen citizen,
        @NotNull Speaker speaker,
        @Nullable UUID speakerId,
        @NotNull String speakerName,
        @NotNull String text,
        @Nullable UUID sessionId,
        @Nullable UUID turnId,
        @NotNull Source source,
        long gameTimeTicks
) {
    public ConversationUtteranceEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(speaker, "speaker");
        Objects.requireNonNull(speakerName, "speakerName");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(source, "source");
        if (text.isBlank()) throw new IllegalArgumentException("text must not be blank");
    }

    public enum Speaker {
        PLAYER,
        CITIZEN,
        /** A note added by an addon or by core, not spoken by anyone. */
        SYSTEM
    }

    public enum Source {
        /** Speech-to-text of live audio: the player's microphone or the citizen's generated voice. */
        TRANSCRIPTION,
        /** Text that was typed or supplied as text, such as a controlled-session player statement. */
        TYPED,
        /** A line from a generated conversation script (citizen pair conversations). */
        SCRIPT
    }
}
