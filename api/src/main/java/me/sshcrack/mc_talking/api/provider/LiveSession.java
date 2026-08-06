package me.sshcrack.mc_talking.api.provider;

import java.util.concurrent.CompletableFuture;

public interface LiveSession {
    void setListener(LiveSessionListener listener);

    LiveSessionListener getListener();

    void sendAudio(short[] pcmAudio);

    void sendText(String text);

    /**
     * Add text to be sent as part of the system prompt after the current turn completes.
     */
    void addPromptTextAfterTalkingComplete(String text);

    void interrupt();

    void close();

    boolean isOpen();

    boolean isClosed();

    boolean isActive();

    CompletableFuture<Void> connect();
}
