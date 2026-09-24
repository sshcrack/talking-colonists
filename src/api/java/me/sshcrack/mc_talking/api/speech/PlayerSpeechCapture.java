package me.sshcrack.mc_talking.api.speech;

import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Turns what a player says through Simple Voice Chat into text, without any citizen (roadmap A10,
 * {@link ApiFeature#PLAYER_SPEECH_CAPTURE}). Meant for items such as a loudspeaker or a
 * microphone: the player uses the item, speaks, and the addon gets the transcript.
 *
 * <p>Only start a capture from an explicit player action (using an item, clicking a block or a
 * button); never capture continuously. While a capture runs the player sees a listening
 * indicator above the hotbar, and their microphone audio goes only to the capture, not to a
 * citizen conversation. The capture ends when the player stops speaking, after a few seconds
 * without speech, or at {@code maxDuration}, whichever comes first. The audio is then sent to
 * the cheap Flash text model once for transcription and discarded.</p>
 *
 * <p>Futures always complete with a {@link SpeechCaptureResult}; failures are typed, never thrown.
 * They complete off the server thread: use {@code server.execute(...)} before touching game state.</p>
 */
public final class PlayerSpeechCapture {
    /** Upper bound for {@code maxDuration}; longer requests are shortened to this. */
    public static final Duration MAX_DURATION = Duration.ofSeconds(30);

    private PlayerSpeechCapture() {
    }

    /**
     * Starts listening to {@code player}. Call on the server thread, in response to an action the
     * player just took. Completes with {@link SpeechCaptureResult.Status#BUSY} if a capture for
     * this player is already running.
     */
    public static @NotNull CompletableFuture<SpeechCaptureResult> capture(@NotNull ServerPlayer player,
                                                                         @NotNull Duration maxDuration) {
        TalkingColonistsApi.requireSupported(ApiFeature.PLAYER_SPEECH_CAPTURE);
        return TalkingColonistsApi.services().playerSpeech().capture(player, maxDuration);
    }

    /** Stops the player's running capture; it completes with {@link SpeechCaptureResult.Status#CANCELLED}. */
    public static boolean cancel(@NotNull ServerPlayer player) {
        TalkingColonistsApi.requireSupported(ApiFeature.PLAYER_SPEECH_CAPTURE);
        return TalkingColonistsApi.services().playerSpeech().cancel(player);
    }

    /** Whether a capture for the player is listening or transcribing. */
    public static boolean isCapturing(@NotNull ServerPlayer player) {
        TalkingColonistsApi.requireSupported(ApiFeature.PLAYER_SPEECH_CAPTURE);
        return TalkingColonistsApi.services().playerSpeech().isCapturing(player);
    }
}
