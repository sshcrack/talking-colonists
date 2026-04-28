package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.prompt.CitizenPromptService;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

public class CitizenConversationGenerator {
    private static final String CONVERSATION_SYSTEM_PROMPT = """
            Generate a dialogue transcript using the structured format below.

            # Summary
            A short summary abourt IMPORTANT messages in this conversation, serving as a memory for the citizens

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

    private static final String FLASH_MODEL = "gemini-3-flash-preview";
    private static final String TTS_MODEL = "gemini-3-flash-tts-preview";

    public static class ConversationGenerationException extends Exception {
        public ConversationGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static List<GeminiTTS.AudioChunk> generateConversation(List<AbstractEntityCitizen> conversationEntities) throws ConversationGenerationException {
        StringBuilder citizenInfo = new StringBuilder();
        citizenInfo.append("-----\n");

        List<GeminiTTS.RequestPayload.SpeakerVoiceConfig> speakerVoiceConfigs = new ArrayList<>();
        for (AbstractEntityCitizen entity : conversationEntities) {
            CitizenPromptView view = CitizenPromptViewFactory.create(entity.getCitizenData(), null);
            citizenInfo.append(CitizenPromptService.generateCitizenRoleplayPrompt(view)).append("\n-----\n");

            var isFemale = view.female();
            var voiceName = CONFIG.currentAiModel.get().getRandomVoice(entity.getUUID(), isFemale);

            speakerVoiceConfigs.add(new GeminiTTS.RequestPayload.SpeakerVoiceConfig() {{
                speaker = view.name();
                voice_config = new GeminiTTS.RequestPayload.VoiceConfig() {{
                    prebuilt_voice_config = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig() {{
                        voice_name = voiceName;
                    }};
                }};
            }});
        }

        String apiKey = McTalkingConfig.CONFIG.geminiApiKey.get();
        String citizenConversation;
        try {
            McTalking.LOGGER.info("Sending conversation generation request to Gemini Flash for {} citizens", conversationEntities.size());
            citizenConversation = GeminiFlash.sendFlashRequest(FLASH_MODEL, apiKey, new GeminiFlash.GenerateContentRequest() {{
                system_instruction = new SystemInstruction();
                system_instruction.parts = List.of(new Part() {{
                    text = CONVERSATION_SYSTEM_PROMPT;
                }});

                contents = new Content();
                contents.parts = List.of(new Part() {{
                    text = citizenInfo.toString();
                }});
            }});
        } catch (InterruptedException | IOException | UnexpectedResponseException e) {
            throw new ConversationGenerationException("Failed to generate conversation using Gemini Flash", e);
        }

        List<GeminiTTS.AudioChunk> audioChunks;
        try {
            McTalking.LOGGER.info("Sending TTS generation request to Gemini TTS for conversation with {} citizens", conversationEntities.size());
            audioChunks = GeminiTTS.generateAudioConversation(TTS_MODEL, apiKey, new GeminiTTS.RequestPayload() {{
                contents = List.of(new GeminiTTS.RequestPayload.Content() {{
                    parts = List.of(new Part() {
                        {
                            text = citizenConversation;
                        }
                    });
                    role = "user";
                }});
                generationConfig = new GeminiTTS.RequestPayload.GenerationConfig() {
                    {
                        responseModalities = List.of("audio");
                        temperature = 1.0;
                        speech_config = new SpeechConfig() {{
                            multi_speaker_voice_config = new MultiSpeakerVoiceConfig() {{
                                speaker_voice_configs = speakerVoiceConfigs;
                            }};
                        }};
                    }
                };
            }});
        } catch (IOException | InterruptedException | UnexpectedResponseException e) {
            throw new ConversationGenerationException("Failed to generate conversation audio using Gemini TTS", e);
        }

        return audioChunks;
    }
}
