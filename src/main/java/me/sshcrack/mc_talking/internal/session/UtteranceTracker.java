package me.sshcrack.mc_talking.internal.session;

import org.jetbrains.annotations.NotNull;

/**
 * Turns one provider session's transcription stream into whole utterances (roadmap A5). Input
 * transcription arrives in chunks while the player talks; it is emitted once, as a single player
 * utterance, when the provider completes its turn. A citizen utterance is emitted when that turn's
 * audio has been heard in full. Nothing is emitted after {@link #end()}.
 */
public final class UtteranceTracker {
    /** Receives finished utterances. */
    public interface Sink {
        void player(@NotNull String text);

        void citizen(@NotNull String text);
    }

    private final Sink sink;
    private final StringBuilder pendingInput = new StringBuilder();
    private boolean ended;

    public UtteranceTracker(@NotNull Sink sink) {
        this.sink = sink;
    }

    public void onInputChunk(@NotNull String chunk) {
        synchronized (this) {
            if (ended) return;
            pendingInput.append(chunk);
        }
    }

    /** The provider finished its turn, so the player's input for it is complete. */
    public void onProviderTurnComplete() {
        String text;
        synchronized (this) {
            if (ended) return;
            text = normalize(pendingInput);
            pendingInput.setLength(0);
        }
        if (!text.isEmpty()) sink.player(text);
    }

    /** The citizen's reply for a turn was played to the end. */
    public void onCitizenTurnHeard(@NotNull String transcript) {
        synchronized (this) {
            if (ended) return;
        }
        String text = transcript.strip();
        if (!text.isEmpty()) sink.citizen(text);
    }

    /** Drops any unfinished input; later callbacks from a closing session are ignored. */
    public void end() {
        synchronized (this) {
            ended = true;
            pendingInput.setLength(0);
        }
    }

    private static String normalize(CharSequence text) {
        return text.toString().strip().replaceAll("\\s+", " ");
    }
}
