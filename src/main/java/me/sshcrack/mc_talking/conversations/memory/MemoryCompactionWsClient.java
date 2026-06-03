package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.ModalityModes;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;

import java.util.function.Consumer;

public class MemoryCompactionWsClient extends GeminiLiveClient {
    private final AbstractEntityCitizen citizen;
    private final CitizenMemories memories;
    private final Consumer<String> onComplete;
    private final Runnable onError;
    private final StringBuilder summaryBuffer = new StringBuilder();
    private volatile boolean completed = false;

    public MemoryCompactionWsClient(AbstractEntityCitizen citizen, CitizenMemories memories,
                                    Consumer<String> onComplete, Runnable onError) {
        super(McTalkingConfig.INSTANCE.instance().geminiApiKey);
        this.citizen = citizen;
        this.memories = memories;
        this.onComplete = onComplete;
        this.onError = onError;
    }

    @Override
    public BidiGenerateContentSetup getSetup() {
        var setup = new BidiGenerateContentSetup("models/" + McTalkingConfig.INSTANCE.instance().currentAiModel.getName());

        setup.generationConfig.responseModalities = ModalityModes.TEXT_AND_AUDIO.getModalities();
        setup.outputAudioTranscription = new JsonObject();

        var citizenData = citizen.getCitizenData();
        if (citizenData != null) {
            setup.generationConfig.speechConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig();
            setup.generationConfig.speechConfig.language_code = McTalkingConfig.INSTANCE.instance().language;
            var female = citizenData.isFemale();
            var uuid = citizen.getUUID();
            setup.generationConfig.speechConfig.voice_config = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.VoiceConfig();
            setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig = new BidiGenerateContentSetup.GenerationConfig.SpeechConfig.PrebuiltVoiceConfig();
            setup.generationConfig.speechConfig.voice_config.prebuiltVoiceConfig.voice_name = McTalkingConfig.INSTANCE.instance().currentAiModel.getRandomVoice(uuid, female);
        }

        setup.realtimeInputConfig = new BidiGenerateContentSetup.RealtimeInputConfig();

        var sys = new BidiGenerateContentSetup.SystemInstruction();
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(
                MemoryCompactionService.SYSTEM_PROMPT
        );
        sys.parts.add(p);
        setup.systemInstruction = sys;

        return setup;
    }

    @Override
    public void onSetupComplete() {
        var input = new RealtimeInput();
        input.text = MemoryCompactionService.buildPrompt(citizen, memories);
        send(ClientMessages.input(input));
    }

    @Override
    public void onGeneratedText(String text) {
        summaryBuffer.append(text);
    }

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
        // We don't process audio and don't expect it
    }

    @Override
    public void onOutputTranscription(String transcription) {
        summaryBuffer.append(transcription);
    }

    @Override
    public void onTurnComplete() {
        completed = true;
        String summary = summaryBuffer.toString().trim();
        if (summary.isEmpty()) {
            McTalking.LOGGER.warn("[MemoryCompaction] Live client produced empty summary for citizen {}",
                    citizen.getCitizenData().getName());
            onError.run();
        } else {
            onComplete.accept(summary);
        }
        close();
    }

    @Override
    public JsonObject onFunctionCall(String name, JsonObject args) {
        return null;
    }

    @Override
    public void onQuotaExceeded() {
        McTalking.LOGGER.warn("[MemoryCompaction] Quota exceeded during Live compaction");
        onError.run();
        close();
    }

    @Override
    public void onError(Exception ex) {
        McTalking.LOGGER.error("[MemoryCompaction] Error during Live compaction", ex);
        onError.run();
        close();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        super.onClose(code, reason, remote);
        if (!completed) {
            McTalking.LOGGER.warn("[MemoryCompaction] Unexpected close for citizen {} (code={}, reason={}, remote={})",
                    citizen.getCitizenData().getName(), code, reason, remote);
            onError.run();
        }
    }

    @Override
    public void addPromptAudio(short[] audio) {
        // We don't want to add prompt audio
    }
}
