package me.sshcrack.mc_talking.pregen;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.AvailableAI;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.config.QuotaTracker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.function.Consumer;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import me.sshcrack.mc_talking.util.AudioHelper;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE;
import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class PregenerationGeminiClient extends GeminiLiveClient {
    private final AbstractEntityCitizen entity;
    private final String promptText;
    private final String modelName;
    private final AvailableAI modelAi;
    private final Consumer<AudioChunk> onComplete;
    private Runnable onError;
    private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
    private static final int MAX_BUFFER_SIZE = 50 * 1024 * 1024; // 50 MB max

    /**
     * Set to {@code true} once {@link #onTurnComplete} fires (successfully or not).
     * Used by {@link #onClose} to decide whether the session ended normally — if
     * {@code false}, the close is treated as an error and the cleanup callback is
     * invoked to release the slot and decrement counters.
     */
    private boolean completed = false;

    public PregenerationGeminiClient(AbstractEntityCitizen entity, String promptText, AvailableAI model, Consumer<AudioChunk> onComplete, Runnable onError) {
        super(McTalkingConfig.INSTANCE.instance().geminiApiKey);
        this.entity = entity;
        this.promptText = promptText;
        this.modelAi = model;
        this.modelName = model.getName();
        this.onComplete = onComplete;
        this.onError = onError;
    }

    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + modelName);
        setup.generationConfig.responseModalities = ModalityModes.AUDIO.getModalities();
        setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
        setup.generationConfig.speechConfig.language_code = McTalkingConfig.INSTANCE.instance().language;

        var female = entity.getCitizenData().isFemale();
        var uuid = entity.getUUID();
        setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
        setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
        setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = modelAi.getRandomVoice(uuid, female);

        var sys = new BidiGenerateContentSetup.SystemInstruction();
        var view = CitizenPromptViewFactory.create(entity.getCitizenData(), new HashMap<>(), null);
        var prompt = CitizenPromptService.generateSystemControlledRoleplayPrompt(view);
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(prompt);
        sys.parts.add(p);
        setup.systemInstruction = sys;

        return setup;
    }

    @Override
    public void onOpen(ServerHandshake data) {
        super.onOpen(data);
    }

    @Override
    public void onSetupComplete() {
        var input = new RealtimeInput();
        input.text = promptText;
        send(ClientMessages.input(input));
    }

    private byte[] resampleToTarget(byte[] data, int currentRate) {
        if (currentRate == TARGET_SAMPLE_RATE) return data;

        short[] shorts = vcApi.getAudioConverter().bytesToShorts(data);
        short[] resampled = AudioHelper.resampleAudio(shorts, currentRate, TARGET_SAMPLE_RATE);

        return vcApi.getAudioConverter().shortsToBytes(resampled);
    }

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        byte[] processed = resampleToTarget(data, sampleRate);
        try {
            if (audioBuffer.size() + processed.length > MAX_BUFFER_SIZE) {
                McTalking.LOGGER.error("Pregenerated audio buffer exceeded maximum size, aborting");
                if (onError != null) {
                    onError.run();
                    onError = null;
                }
                close();
                return;
            }
            audioBuffer.write(processed);
        } catch (IOException e) {
            McTalking.LOGGER.error("Failed to write pregenerated audio", e);
        }
    }

    @Override
    public void onTurnComplete() {
        completed = true;
        byte[] audioData = audioBuffer.toByteArray();
        if (audioData.length > 0) {
            onComplete.accept(new AudioChunk(audioData, TARGET_SAMPLE_RATE));
        } else {
            McTalking.LOGGER.warn("Pregeneration completed without producing audio");
            if (onError != null) {
                onError.run();
                onError = null;
            }
        }
        close();
    }

    @Override
    public void onGeneratedText(String text) {
        // Not used for pregen
    }

    @Override
    public void onOutputTranscription(String transcription) {
        // Not used
    }

    @Override
    public JsonObject onFunctionCall(String name, JsonObject args) {
        return null; // Tools disabled for pregen
    }

    @Override
    public void onQuotaExceeded() {
        McTalking.LOGGER.warn("Quota exceeded during audio pregeneration");
        QuotaTracker.reportQuotaExceeded(modelName);
        if (onError != null) {
            onError.run();
            onError = null;
        }
        close();
    }

    @Override
    public void onError(Exception ex) {
        McTalking.LOGGER.error("Error during pregeneration", ex);
        if (onError != null) {
            onError.run();
            onError = null;
        }
        close();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        try {
            super.onClose(code, reason, remote);
        } catch (Exception e) {
            McTalking.LOGGER.error("Error in PregenerationGeminiClient.onClose", e);
        }

        // If the session closed before onTurnComplete fired (e.g. auth failure,
        // server-side error during setup, or an abnormal WebSocket close), clean
        // up the slot and counters so no stale entries are left behind.
        if (!completed && onError != null) {
            McTalking.LOGGER.warn("Pregeneration session closed before completion (code={}, reason={})", code, reason);
            onError.run();
            onError = null;
        }
    }

    @Override
    public void addPromptAudio(short[] audio) {
        // Not used for pregen
    }
}
