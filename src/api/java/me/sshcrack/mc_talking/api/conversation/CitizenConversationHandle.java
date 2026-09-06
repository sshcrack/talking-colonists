package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Safe handle for an addon-started citizen pair conversation. The transport, audio stream, slot
 * ownership and Gemini clients remain internal to Talking Colonists.
 */
public interface CitizenConversationHandle {
    enum State {
        READY,
        GENERATING,
        PLAYING_AUDIO,
        ENDED
    }

    /** Starts the conversation once. Repeated calls are ignored. */
    void start();

    /** Cancels the conversation. Safe to call repeatedly. */
    void cancel();

    @NotNull State state();

    /**
     * Replaces the state listener. Events are delivered on the Minecraft server thread.
     */
    void setStateListener(@NotNull Consumer<State> listener);
}
