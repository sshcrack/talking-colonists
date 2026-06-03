package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.TtsQuotaManager;
import me.sshcrack.mc_talking.conversations.memory.CitizenMemoryGenerator;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class CitizenConversationGenerator {
    private static final String CONVERSATION_SYSTEM_PROMPT = """
            Generate a dialogue transcript using the structured format below.
            Participants in this conversation are citizens from the MineColonies mod, each with unique personalities, needs, and relationships. Use the provided citizen information to create an immersive and realistic conversation that reflects their current states and emotional profiles.
            The citizens can not give each other blocks or items, the "manager" of the colony can however.
            Try to incorporate talking about the manager in the colony in this conversation.

            # Audio Profile
            For each character: Describe their vocal identity (tone, personality, emotional baseline).

            # Director's Note
            For each character include:
            - Style (e.g., empathetic, authoritative, unstable, etc.)
            - Pace (e.g., slow, staccato, rushed)
            - Accent (e.g., British RP, American Gen, regional, etc.)

            ## Scene:
            Describe the environment and atmosphere in 1-2 sentences.

            ## Sample Context:
            Define genre, tone, pacing behavior, and emotional tension.

            ## Transcript:
            Write a multi-character dialogue using the provided citizen names instead of generic speaker labels.

            Formatting rules:
            - Use character names exactly as given (e.g., "Tomas Reed:")
            - Include emotional/action cues in square brackets before or within lines (e.g., [shouting], [weakly], [suspicion])
            - Keep dialogue concise but expressive
            - Reflect each character's emotional state, needs, and personality
            - Escalate tension naturally toward the end if appropriate
            - Ensure characters reference their personal conditions, needs, and relationships when relevant
            - Maintain immersive, roleplay-style dialogue

            ---

            ## Input Data:
            You will receive one or more citizens in this format:

            # CITIZEN INFO
            Name: <Full Name>

            Type: <Description>

            ## RELATIONSHIPS
            <List of relationships>

            ## CURRENT STATE
            <List of physical and emotional conditions>

            ## NEEDS (first person)
            <List of urgent needs>

            ## EMOTIONAL PROFILE
            <Behavioral tendencies>

            ---

            ## Task:
            - Generate a realistic, immersive conversation between the given citizens
            - Ensure each character speaks according to their emotional profile and current condition
            - Incorporate their needs naturally into dialogue
            - Match tone and pacing to the context

            ## Rules
            - The transcript is the exact words the model will speak. An audio tag is a word in square brackets that indicates either how something should be said, a change of tone, or an interjection.
            - You can control style, tone, accent, and pace using natural language prompts or audio tags.
            - For audio tags, make sure to only focus on voices, not physical actions or sounds.
            Example:
            ```
            Thomas: I know right, [sarcastically] I couldn't believe it. [whispers] She should have totally left
            at that point.

            Nils: [cough] Well, [sighs] I guess it doesn't matter now.
            ```
            """;

    private static GeminiTTS.RequestPayload getTTSPrompt(String conversation, List<GeminiTTS.RequestPayload.SpeakerVoiceConfig> speakerVoiceConfigs) {
        GeminiTTS.RequestPayload payload = new GeminiTTS.RequestPayload();

        GeminiTTS.RequestPayload.Content content = new GeminiTTS.RequestPayload.Content();

        GeminiTTS.RequestPayload.Part part = new GeminiTTS.RequestPayload.Part();
        part.text = conversation;

        content.parts = List.of(part);
        content.role = "user";

        payload.contents = List.of(content);

        GeminiTTS.RequestPayload.GenerationConfig generationConfig =
                new GeminiTTS.RequestPayload.GenerationConfig();

        generationConfig.responseModalities = List.of("AUDIO");
        generationConfig.temperature = 1.0;

        GeminiTTS.RequestPayload.SpeechConfig speechConfig = new GeminiTTS.RequestPayload.SpeechConfig();

        GeminiTTS.RequestPayload.MultiSpeakerVoiceConfig multiSpeakerVoiceConfig = new GeminiTTS.RequestPayload.MultiSpeakerVoiceConfig();
        multiSpeakerVoiceConfig.speaker_voice_configs = speakerVoiceConfigs;

        speechConfig.multi_speaker_voice_config = multiSpeakerVoiceConfig;

        generationConfig.speech_config = speechConfig;

        payload.generationConfig = generationConfig;

        return payload;
    }

    private static GeminiFlash.GenerateContentRequest getFlashPrompt(String citizenInfo) {
        GeminiFlash.GenerateContentRequest request = new GeminiFlash.GenerateContentRequest();

        GeminiFlash.GenerateContentRequest.SystemInstruction systemInstruction = new GeminiFlash.GenerateContentRequest.SystemInstruction();
        GeminiFlash.GenerateContentRequest.Part systemPart = new GeminiFlash.GenerateContentRequest.Part();
        systemPart.text = CONVERSATION_SYSTEM_PROMPT;
        systemInstruction.parts = List.of(systemPart);
        request.system_instruction = systemInstruction;

        GeminiFlash.GenerateContentRequest.Content content = new GeminiFlash.GenerateContentRequest.Content();
        GeminiFlash.GenerateContentRequest.Part contentPart = new GeminiFlash.GenerateContentRequest.Part();
        contentPart.text = citizenInfo;
        content.parts = List.of(contentPart);
        request.contents = content;

        return request;
    }

    public static void generateConversation(List<AbstractEntityCitizen> conversationEntities, MinecraftServer server, Consumer<GeminiTTS.AudioChunk> chunkConsumer) throws ConversationGenerationException {
        StringBuilder citizenInfo = new StringBuilder();
        citizenInfo.append("-----\n");

        List<GeminiTTS.RequestPayload.SpeakerVoiceConfig> speakerVoiceConfigs = new ArrayList<>();
        Map<UUID, String> interestedParties = new HashMap<>();
        conversationEntities.forEach(e -> interestedParties.put(e.getUUID(), e.getCitizenData().getName()));

        for (AbstractEntityCitizen entity : conversationEntities) {
            CitizenPromptView view = CitizenPromptViewFactory.create(entity.getCitizenData(), interestedParties, null);
            citizenInfo.append(CitizenPromptService.generateConversationalInfoPrompt(view)).append("\n-----\n");

            var isFemale = view.female();
            var voiceName = McTalkingConfig.INSTANCE.instance().currentAiModel.getRandomVoice(entity.getUUID(), isFemale);

            var config = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();

            config.speaker = view.name();
            config.voice_config = new GeminiTTS.RequestPayload.VoiceConfig();
            config.voice_config.prebuilt_voice_config = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
            config.voice_config.prebuilt_voice_config.voice_name = voiceName;

            speakerVoiceConfigs.add(config);
        }

        RawConversation conversation = generateConversationAndMemory(conversationEntities, citizenInfo, server);

        String apiKey = McTalkingConfig.INSTANCE.instance().geminiApiKey;
        try {
            McTalking.LOGGER.info("Sending TTS generation request to Gemini TTS for conversation with {} citizens", conversationEntities.size());
            GeminiTTS.streamGenerateAudioConversation(McTalkingConfig.TTS_MODEL, apiKey, getTTSPrompt(conversation.conversation(), speakerVoiceConfigs), chunkConsumer);
            TtsQuotaManager.reportSuccess();
        } catch (IOException | UnexpectedResponseException e) {
            McTalking.LOGGER.error("Failed to generate conversation audio using Gemini TTS", e);
            TtsQuotaManager.reportFailure(e);
            throw new ConversationGenerationException("Failed to generate conversation audio using Gemini TTS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            McTalking.LOGGER.error("Conversation audio generation was interrupted", e);
            TtsQuotaManager.reportFailure(e);
            throw new ConversationGenerationException("Conversation audio generation was interrupted", e);
        }

        //REVIEW: Maybe only save if the conversation has been finished fully
        var generator = conversation.generator();
        if(generator != null)
            generator.scheduleOrSaveMemory();
    }

    public record RawConversation(String conversation, @Nullable CitizenMemoryGenerator generator) {

    }

    @NotNull
    private static RawConversation generateConversationAndMemory(List<AbstractEntityCitizen> conversationEntities, StringBuilder citizenInfo, MinecraftServer server) throws ConversationGenerationException {
        String apiKey = McTalkingConfig.INSTANCE.instance().geminiApiKey;
        String rawConversationOutput;
        try {
            McTalking.LOGGER.info("Sending conversation generation request to Gemini Flash for {} citizens", conversationEntities.size());
            rawConversationOutput = GeminiFlash.sendFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, getFlashPrompt(citizenInfo.toString()));
        } catch (IOException | UnexpectedResponseException e) {
            McTalking.LOGGER.error("Failed to generate conversation using Gemini Flash", e);
            TtsQuotaManager.reportFailure(e);
            throw new ConversationGenerationException("Failed to generate conversation using Gemini Flash", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            McTalking.LOGGER.error("Conversation generation was interrupted", e);
            TtsQuotaManager.reportFailure(e);
            throw new ConversationGenerationException("Conversation generation was interrupted", e);
        }

        CitizenMemoryGenerator generator = null;
        if (McTalkingConfig.INSTANCE.instance().enableConversationSummaryAndMemorize) {
            generator = CitizenMemoryGenerator.addAndGenerateMemory(rawConversationOutput, conversationEntities, server);
        }
        return new RawConversation(rawConversationOutput, generator);
    }
}
