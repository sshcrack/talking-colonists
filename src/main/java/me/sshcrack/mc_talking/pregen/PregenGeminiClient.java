package me.sshcrack.mc_talking.pregen;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.gemini_live_lib.websocket.handshake.ServerHandshake;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.ModalityModes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

public class PregenGeminiClient extends GeminiLiveClient {
    private final AbstractEntityCitizen entity;
    private final String promptText;
    private final Consumer<byte[]> onComplete;
    private final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

    public PregenGeminiClient(AbstractEntityCitizen entity, String promptText, Consumer<byte[]> onComplete) {
        super(McTalkingConfig.INSTANCE.instance().geminiApiKey);
        this.entity = entity;
        this.promptText = promptText;
        this.onComplete = onComplete;
    }

    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + McTalkingConfig.INSTANCE.instance().currentAiModel.getName());
        setup.generationConfig.responseModalities = ModalityModes.AUDIO.getModalities();
        setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
        setup.generationConfig.speechConfig.language_code = McTalkingConfig.INSTANCE.instance().language;

        var female = entity.getCitizenData().isFemale();
        var uuid = entity.getUUID();
        setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
        setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
        setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = McTalkingConfig.INSTANCE.instance().currentAiModel.getRandomVoice(uuid, female);

        var sys = new BidiGenerateContentSetup.SystemInstruction();
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(
                "You are " + entity.getCitizenData().getName() + ", a citizen in a Minecraft colony."
        );
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

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        try {
            audioBuffer.write(data);
        } catch (IOException e) {
            McTalking.LOGGER.error("Failed to write pregenerated audio", e);
        }
    }

    @Override
    public void onTurnComplete() {
        byte[] audioData = audioBuffer.toByteArray();
        if (audioData.length > 0) {
            onComplete.accept(audioData);
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
        close();
    }

    @Override
    public void onError(Exception ex) {
        McTalking.LOGGER.error("Error during pregeneration", ex);
        close();
    }

    @Override
    public void addPromptAudio(short[] audio) {
        // Not used for pregen
    }
}
