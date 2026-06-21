package me.sshcrack.mc_talking.api.session;

import com.google.gson.JsonElement;
import me.sshcrack.mc_talking.api.audio.AudioChunk;

import java.util.function.Consumer;

public interface BundledSession {
    // Input
    void sendAudio(short[] pcmData, int sampleRate);
    void sendText(String text);
    void interrupt();

    // Output callbacks (set before start)
    BundledSession onText(Consumer<String> handler);
    BundledSession onAudio(Consumer<AudioChunk> handler);
    BundledSession onStt(Consumer<String> handler);
    BundledSession onTurnComplete(Runnable handler);
    BundledSession onError(Consumer<Throwable> handler);
    BundledSession onToolCall(ToolCallHandler handler);
    BundledSession onQuotaExceeded(Runnable handler);

    // Tool responses
    void respondToToolCall(String toolCallId, JsonElement result);

    // Lifecycle
    void start();
    void close();
    boolean isActive();
}
