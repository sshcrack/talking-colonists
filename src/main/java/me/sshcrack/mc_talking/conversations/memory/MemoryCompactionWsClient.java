package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonObject;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.GeminiLiveClient;
import me.sshcrack.gemini_live_lib.gson.BidiGenerateContentSetup;
import me.sshcrack.gemini_live_lib.gson.ClientMessages;
import me.sshcrack.gemini_live_lib.gson.RealtimeInput;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;

import java.util.List;
import java.util.function.Consumer;

public class MemoryCompactionWsClient extends GeminiLiveClient {
    private final AbstractEntityCitizen citizen;
    private final CitizenMemories memories;
    private final Consumer<String> onComplete;
    private final Runnable onError;
    private final StringBuilder summaryBuffer = new StringBuilder();

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

        setup.generationConfig.responseModalities = List.of("TEXT", "AUDIO");

        var sys = new BidiGenerateContentSetup.SystemInstruction();
        var p = new BidiGenerateContentSetup.SystemInstruction.Part(
                "You are a memory summarization assistant for a Minecraft colony citizen. " +
                        "Given the citizen's events and facts, produce a concise first-person summary. " +
                        "Keep recent and significant information. Discard minor details. " +
                        "Output only the summary text, no labels or formatting."
        );
        sys.parts.add(p);
        setup.systemInstruction = sys;

        return setup;
    }

    @Override
    public void onSetupComplete() {
        String prompt = buildPrompt();
        var input = new RealtimeInput();
        input.text = prompt;
        send(ClientMessages.input(input));
    }

    private String buildPrompt() {
        String name = citizen.getCitizenData().getName();
        StringBuilder sb = new StringBuilder();
        sb.append("Summarize these memories for ").append(name).append(":\n\n");

        if (!memories.getEvents().isEmpty()) {
            sb.append("Events:\n");
            for (String event : memories.getEvents()) {
                sb.append("- ").append(event).append("\n");
            }
            sb.append("\n");
        }

        if (!memories.getFacts().isEmpty()) {
            sb.append("Facts:\n");
            for (String fact : memories.getFacts()) {
                sb.append("- ").append(fact).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Summary:");
        return sb.toString();
    }

    @Override
    public void onGeneratedText(String text) {
        summaryBuffer.append(text);
    }

    @Override
    public void onGeneratedAudio(byte[] data, int sampleRate) {
    }

    @Override
    public void onOutputTranscription(String transcription) {
    }

    @Override
    public void onTurnComplete() {
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
    public void addPromptAudio(short[] audio) {
    }
}
