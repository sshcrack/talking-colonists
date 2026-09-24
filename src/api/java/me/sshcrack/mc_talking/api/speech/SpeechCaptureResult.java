package me.sshcrack.mc_talking.api.speech;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Outcome of a {@link PlayerSpeechCapture#capture} request.
 *
 * @param status     what happened
 * @param transcript what the player said, set exactly when the status is {@link Status#TRANSCRIBED}
 * @param speech     length of the audio that was captured (zero when nothing was)
 * @param detail     human-readable reason for failures (never contains credentials or audio)
 */
public record SpeechCaptureResult(@NotNull Status status, @Nullable String transcript, @NotNull Duration speech,
                                  @Nullable String detail) {
    public enum Status {
        /** The player spoke and {@link #transcript()} holds the text. */
        TRANSCRIBED,
        /** The player did not speak before the silence timeout or the maximum duration, or the audio had no intelligible speech. */
        NO_SPEECH,
        /** The capture was cancelled by {@link PlayerSpeechCapture#cancel} or because the server is stopping. */
        CANCELLED,
        /** The player left the server during the capture. */
        PLAYER_LEFT,
        /** A capture for this player is already running. */
        BUSY,
        /** The player is not connected to Simple Voice Chat, or has it disabled. */
        NO_VOICE_CHAT,
        /** The transcription model's quota is exhausted; retry after it resets. */
        QUOTA,
        /** No Gemini API key is configured. */
        UNAVAILABLE,
        /** The transcription request failed for another reason (network, server error). */
        PROVIDER_ERROR
    }

    public SpeechCaptureResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(speech, "speech");
        if ((status == Status.TRANSCRIBED) != (transcript != null)) {
            throw new IllegalArgumentException("transcript is set exactly when the status is TRANSCRIBED");
        }
    }

    public static @NotNull SpeechCaptureResult transcribed(@NotNull String transcript, @NotNull Duration speech) {
        return new SpeechCaptureResult(Status.TRANSCRIBED, Objects.requireNonNull(transcript, "transcript"), speech, null);
    }

    public static @NotNull SpeechCaptureResult failure(@NotNull Status status, @NotNull Duration speech,
                                                       @Nullable String detail) {
        if (status == Status.TRANSCRIBED) throw new IllegalArgumentException("use transcribed(...)");
        return new SpeechCaptureResult(status, null, speech, detail);
    }

    public boolean isTranscribed() {
        return status == Status.TRANSCRIBED;
    }
}
