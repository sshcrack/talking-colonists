package me.sshcrack.mc_talking.session;

import me.sshcrack.mc_talking.api.provider.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A {@link LiveSession} implementation that runs a sequential pipeline:
 * STT → LLM → TTS.
 * <p>
 * No real-time streaming, no interruptions — each input produces a complete
 * output through the entire pipeline. This is a documented, permanent semantic
 * gap versus bundled (Gemini Live) sessions.
 */
public class LocalComposableSession implements LiveSession {
    private static final Logger LOGGER = LoggerFactory.getLogger("LocalComposableSession");

    private final SttProvider stt;
    private final LlmProvider llm;
    private final TtsProvider tts;
    private final LiveSessionConfig config;
    private volatile LiveSessionListener listener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "local-composable-session");
        t.setDaemon(true);
        return t;
    });

    private final List<String> pendingTextAfterTalking = new CopyOnWriteArrayList<>();
    private final List<short[]> pendingAudio = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean closed = false;

    public LocalComposableSession(SttProvider stt, LlmProvider llm, TtsProvider tts, LiveSessionConfig config) {
        this.stt = stt;
        this.llm = llm;
        this.tts = tts;
        this.config = config;
    }

    @Override
    public void setListener(LiveSessionListener listener) {
        this.listener = listener;
    }

    @Override
    public LiveSessionListener getListener() {
        return listener;
    }

    @Override
    public void sendAudio(short[] pcmAudio) {
        if (closed) return;
        pendingAudio.add(pcmAudio);
        processPipeline();
    }

    @Override
    public void sendText(String text) {
        if (closed) return;
        processTextPipeline(text);
    }

    @Override
    public void addPromptTextAfterTalkingComplete(String text) {
        pendingTextAfterTalking.add(text);
    }

    @Override
    public void interrupt() {
        // Not supported in composable mode
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isActive() {
        return !closed;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.completedFuture(null);
    }

    private void processPipeline() {
        executor.submit(() -> {
            try {
                List<short[]> batch;
                synchronized (pendingAudio) {
                    if (pendingAudio.isEmpty()) return;
                    batch = new ArrayList<>(pendingAudio);
                    pendingAudio.clear();
                }

                // Step 1: STT
                String userText;
                if (!batch.isEmpty()) {
                    byte[] pcmBytes = shortsToBytes(batch.get(0));
                    AudioData audio = new AudioData(pcmBytes, 16000, 1);
                    userText = stt.transcribe(audio).get();
                    var l = listener;
                    if (l != null) l.onInputTranscription(userText);
                } else {
                    return;
                }

                // Step 2: LLM
                String fullPrompt = userText;
                if (!pendingTextAfterTalking.isEmpty()) {
                    fullPrompt = String.join("\n", pendingTextAfterTalking) + "\n" + userText;
                    pendingTextAfterTalking.clear();
                }

                var llmRequest = LlmRequest.builder()
                        .systemPrompt(config.systemPrompt())
                        .tools(config.tools())
                        .userText(fullPrompt)
                        .build();

                LlmResponse llmResponse = llm.generate(llmRequest).get();

                var l = listener;
                if (l != null) l.onGeneratedText(llmResponse.text());

                // Handle tool calls via round trip if present
                if (llmResponse.hasToolCalls()) {
                    for (ToolCall toolCall : llmResponse.toolCalls()) {
                        if (l != null) l.onToolCall(toolCall);
                    }
                }

                // Step 3: TTS
                if (!llmResponse.text().isBlank()) {
                    var ttsRequest = TtsRequest.builder()
                            .text(llmResponse.text())
                            .voice(config.voice())
                            .language(config.language())
                            .build();

                    AudioData audioData = tts.synthesize(ttsRequest).get();
                    if (l != null) {
                        l.onGeneratedAudio(audioData);
                        l.onTurnComplete();
                        l.onGenerationComplete();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error in composable session pipeline", e);
                var l = listener;
                if (l != null) l.onError(e);
            }
        });
    }

    private void processTextPipeline(String text) {
        executor.submit(() -> {
            try {
                var llmRequest = LlmRequest.builder()
                        .systemPrompt(config.systemPrompt())
                        .tools(config.tools())
                        .userText(text)
                        .build();

                LlmResponse llmResponse = llm.generate(llmRequest).get();

                var l = listener;
                if (l != null) l.onGeneratedText(llmResponse.text());

                if (!llmResponse.text().isBlank()) {
                    var ttsRequest = TtsRequest.builder()
                            .text(llmResponse.text())
                            .voice(config.voice())
                            .language(config.language())
                            .build();

                    AudioData audioData = tts.synthesize(ttsRequest).get();
                    if (l != null) {
                        l.onGeneratedAudio(audioData);
                        l.onTurnComplete();
                        l.onGenerationComplete();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error in composable text pipeline", e);
                var l = listener;
                if (l != null) l.onError(e);
            }
        });
    }

    private static byte[] shortsToBytes(short[] shorts) {
        byte[] bytes = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            bytes[i * 2] = (byte) (shorts[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((shorts[i] >> 8) & 0xFF);
        }
        return bytes;
    }
}
