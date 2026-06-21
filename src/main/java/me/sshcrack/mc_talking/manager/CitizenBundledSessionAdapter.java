package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.audio.AudioChunk;
import me.sshcrack.mc_talking.api.audio.AudioFormat;
import me.sshcrack.mc_talking.api.provider.AiProvider;
import me.sshcrack.mc_talking.api.provider.Capability;
import me.sshcrack.mc_talking.api.provider.PresetDefinition;
import me.sshcrack.mc_talking.api.session.BundledSession;
import me.sshcrack.mc_talking.api.session.ToolCallHandler;
import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class CitizenBundledSessionAdapter implements BundledSession {
    private final CitizenWsClient client;
    private final AbstractEntityCitizen citizen;
    private final PresetDefinition preset;

    private Consumer<String> onTextHandler = s -> {};
    private Consumer<AudioChunk> onAudioHandler = s -> {};
    private Consumer<String> onSttHandler = s -> {};
    private Runnable onTurnCompleteHandler = () -> {};
    private Consumer<Throwable> onErrorHandler = e -> {};
    private ToolCallHandler onToolCallHandler = null;
    private Runnable onQuotaExceededHandler = () -> {};

    private volatile boolean active = false;

    public CitizenBundledSessionAdapter(CitizenWsClient client, AbstractEntityCitizen citizen, PresetDefinition preset) {
        this.client = client;
        this.citizen = citizen;
        this.preset = preset;
    }

    @Override
    public void sendAudio(short[] pcmData, int sampleRate) {
        client.addPromptAudio(pcmData);
    }

    @Override
    public void sendText(String text) {
        client.addPromptTextImmediate(text);
    }

    @Override
    public void interrupt() {
        // CitizenWsClient does not expose an interrupt API directly;
        // the GeminiLiveClient handles interruption server-side via the WebSocket.
    }

    @Override
    public BundledSession onText(Consumer<String> handler) {
        this.onTextHandler = handler;
        return this;
    }

    @Override
    public BundledSession onAudio(Consumer<AudioChunk> handler) {
        this.onAudioHandler = handler;
        return this;
    }

    @Override
    public BundledSession onStt(Consumer<String> handler) {
        this.onSttHandler = handler;
        return this;
    }

    @Override
    public BundledSession onTurnComplete(Runnable handler) {
        this.onTurnCompleteHandler = handler;
        return this;
    }

    @Override
    public BundledSession onError(Consumer<Throwable> handler) {
        this.onErrorHandler = handler;
        return this;
    }

    @Override
    public BundledSession onToolCall(ToolCallHandler handler) {
        this.onToolCallHandler = handler;
        return this;
    }

    @Override
    public BundledSession onQuotaExceeded(Runnable handler) {
        this.onQuotaExceededHandler = handler;
        return this;
    }

    @Override
    public void respondToToolCall(String toolCallId, JsonElement result) {
        // Not directly supported via CitizenWsClient - tool calls are auto-handled by Gemini
    }

    @Override
    public void start() {
        active = true;
        client.connect();
    }

    @Override
    public void close() {
        active = false;
        client.close();
    }

    @Override
    public boolean isActive() {
        return active && !client.isClosed();
    }

    public CitizenWsClient getClient() {
        return client;
    }

    public AbstractEntityCitizen getCitizen() {
        return citizen;
    }

    public PresetDefinition getPreset() {
        return preset;
    }
}
