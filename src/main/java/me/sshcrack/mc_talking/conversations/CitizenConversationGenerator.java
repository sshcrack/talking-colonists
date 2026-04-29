package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.conversations.memory.CitizenMemoryGenerator;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class CitizenConversationGenerator {
    private static final String CONVERSATION_SYSTEM_PROMPT = """
            Generate a dialogue transcript using the structured format below.
            Participants in this conversation are citizens from the MineColonies mod, each with unique personalities, needs, and relationships. Use the provided citizen information to create an immersive and realistic conversation that reflects their current states and emotional profiles.
            The citizens can not give each other blocks or items, the "manager" of the colony can however.

            # Audio Profile
            For each character: Describe their vocal identity (tone, personality, emotional baseline).

            # Director's Note
            For each character include:
            - Style (e.g., empathetic, authoritative, unstable, etc.)
            - Pace (e.g., slow, staccato, rushed)
            - Accent (e.g., British RP, American Gen, regional, etc.)

            ## Scene:
            Describe the environment and atmosphere in 1–2 sentences.

            ## Sample Context:
            Define genre, tone, pacing behavior, and emotional tension.

            ## Transcript:
            Write a multi-character dialogue using the provided citizen names instead of generic speaker labels.

            Formatting rules:
            - Use character names exactly as given (e.g., "Tomas Reed:")
            - Include emotional/action cues in square brackets before or within lines (e.g., [shouting], [weakly], [suspicion])
            - Keep dialogue concise but expressive
            - Reflect each character’s emotional state, needs, and personality
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
            - Do NOT summarize — output ONLY the formatted transcript
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

        generationConfig.responseModalities = List.of("audio");
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

    public static List<GeminiTTS.AudioChunk> generateConversation(List<AbstractEntityCitizen> conversationEntities, MinecraftServer server) throws ConversationGenerationException {
        StringBuilder citizenInfo = new StringBuilder();
        citizenInfo.append("-----\n");

        List<GeminiTTS.RequestPayload.SpeakerVoiceConfig> speakerVoiceConfigs = new ArrayList<>();
        Map<UUID, String> interestedParties = new HashMap<>();
        conversationEntities.forEach(e -> interestedParties.put(e.getUUID(), e.getCitizenData().getName()));

        for (AbstractEntityCitizen entity : conversationEntities) {
            CitizenPromptView view = CitizenPromptViewFactory.create(entity.getCitizenData(), interestedParties, null);
            citizenInfo.append(CitizenPromptService.generateCitizenRoleplayPrompt(view)).append("\n-----\n");

            var isFemale = view.female();
            var voiceName = CONFIG.currentAiModel.get().getRandomVoice(entity.getUUID(), isFemale);

            var config = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();

            config.speaker = view.name();
            config.voice_config = new GeminiTTS.RequestPayload.VoiceConfig();
            config.voice_config.prebuilt_voice_config = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
            config.voice_config.prebuilt_voice_config.voice_name = voiceName;

            speakerVoiceConfigs.add(config);
        }

        String conversation = generateRawConversation(conversationEntities, citizenInfo, server);
/*
        String apiKey = McTalkingConfig.CONFIG.geminiApiKey.get();
        StringBuilder conversation = new StringBuilder();
        conversation.append("""
                # Audio Profile
                Tucker L. Gasper: Energetisch, hell und freundlich, jedoch mit einem leicht nervösen Unterton aufgrund von Sicherheitsbedenken.
                Lennon G. Fletcher: Tief, resonant und ruhig, mit einem nachdenklichen, fast meditativen Rhythmus.

                # Director's Note
                Tucker L. Gasper:
                - Style: Enthusiastisch und hilfsbereit
                - Pace: Gehetzt
                - Accent: Standarddeutsch

                Lennon G. Fletcher:
                - Style: Spirituell und besonnen
                - Pace: Langsam und bedächtig
                - Accent: Norddeutscher Einschlag (ruhig)

                ## Scene:
                Zwei Baustellen, die nah beieinander liegen. Der Wind weht durch die Gerüste, während die beiden Arbeiter frustriert auf ihre leeren Materialkisten starren.

                ## Sample Context:
                Genre: Slice-of-Life / Rollenspiel. Ton: Freundlich, aber von Bedürfnissen und leichter Paranoia geprägt. Pacing: Alltäglich, mit einer Steigerung der sozialen Sehnsucht.

                ## Transcript:
                Tucker L. Gasper: [ruft herüber, während er nervös die Umgebung scannt] Hallo Lennon! Schön, mal ein bekanntes Gesicht zu sehen. Ich stecke hier total fest. Mir fehlt genau eine Fichtenholztreppe, um die Ecke dort oben abzuschließen. Hast du zufällig etwas gesehen?

                Lennon G. Fletcher: [seufzt ruhig und reibt sich den Bauch] Leider nein, Tucker. Bei mir sieht es nicht besser aus. Ich warte auf bis zu achtundfünfzig Pflastersteine, sonst geht es hier gar nicht weiter. Außerdem bringt mich dieser Hunger noch um... es gibt im Speisesaal einfach jeden Tag das Gleiche. Ein bisschen Abwechslung würde meiner Seele gut tun.

                Tucker L. Gasper: [optimistisch, aber mit einem Zittern in der Stimme] Ach, der Manager wird uns sicher bald versorgen. Aber sag mal, fühlst du dich hier heute auch so unwohl? Ich habe ständig das Gefühl, wir sind nicht ausreichend geschützt. Es ist so still in der Kolonie.

                Lennon G. Fletcher: [nickt langsam] Die spirituellen Schwingungen sind heute in der Tat unruhig. Wir arbeiten hier so isoliert vor uns hin, man fühlt sich fast vergessen. Ein bisschen mehr Gesellschaft und ein sicheres Dach über dem Kopf wären jetzt genau das Richtige.

                Tucker L. Gasper: [eifrig] Ganz genau! Wenn ich erst diese eine Treppe habe, fühle ich mich zumindest produktiv. Wir müssen einfach darauf hoffen, dass der Manager uns hört. Ohne das Material sind uns die Hände gebunden.

                Lennon G. Fletcher: [blickt in die Ferne] So ist es. Ich brauche diesen Pflasterstein, um die Struktur zu festigen. Hoffen wir, dass bald jemand vorbeikommt, Tucker. Die Einsamkeit hier oben auf dem Gerüst ist fast so schwer wie der Hunger.

                """);
        List<GeminiTTS.RequestPayload.SpeakerVoiceConfig> speakerVoiceConfigs = (new Gson()).fromJson("""
                [{"speaker":"Tucker L. Gasper","voice_config":{"prebuilt_voice_config":{"voice_name":"Sadachbia"}}},{"speaker":"Lennon G. Fletcher","voice_config":{"prebuilt_voice_config":{"voice_name":"Enceladus"}}}]

                """, new TypeToken<List<GeminiTTS.RequestPayload.SpeakerVoiceConfig>>() {
        });
*/

        List<GeminiTTS.AudioChunk> audioChunks;
        String apiKey = CONFIG.geminiApiKey.get();
        try {
            McTalking.LOGGER.info("Sending TTS generation request to Gemini TTS for conversation with {} citizens", conversationEntities.size());
            audioChunks = GeminiTTS.generateAudioConversation(McTalkingConfig.TTS_MODEL, apiKey, getTTSPrompt(conversation, speakerVoiceConfigs));
        } catch (IOException | UnexpectedResponseException e) {
            throw new ConversationGenerationException("Failed to generate conversation audio using Gemini TTS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversationGenerationException("Conversation audio generation was interrupted", e);
        }

        return audioChunks;
    }

    @NotNull
    private static String generateRawConversation(List<AbstractEntityCitizen> conversationEntities, StringBuilder citizenInfo, MinecraftServer server) throws ConversationGenerationException {
        String apiKey = CONFIG.geminiApiKey.get();
        String rawConversationOutput;
        try {
            McTalking.LOGGER.info("Sending conversation generation request to Gemini Flash for {} citizens", conversationEntities.size());
            rawConversationOutput = GeminiFlash.sendFlashRequest(McTalkingConfig.FLASH_MODEL, apiKey, getFlashPrompt(citizenInfo.toString()));
        } catch (IOException | UnexpectedResponseException e) {
            throw new ConversationGenerationException("Failed to generate conversation using Gemini Flash", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConversationGenerationException("Conversation generation was interrupted", e);
        }

        CitizenMemoryGenerator.addAndGenerateMemory(rawConversationOutput, conversationEntities, server);

        return rawConversationOutput;
    }
}
