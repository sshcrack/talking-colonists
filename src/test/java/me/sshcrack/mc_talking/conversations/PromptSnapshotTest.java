package me.sshcrack.mc_talking.conversations;

import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.mc_talking.api.pregen.PregenerationKind;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.internal.text.TextPrompts;
import me.sshcrack.mc_talking.pregen.PregenerationPrompts;
import me.sshcrack.mc_talking.testing.PromptSnapshots;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import me.sshcrack.mc_talking.util.MiscUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static me.sshcrack.mc_talking.testing.CitizenPromptViewFixture.citizen;
import static me.sshcrack.mc_talking.testing.CitizenPromptViewFixture.secondCitizen;

/**
 * Snapshots of every prompt path the mod sends to Gemini, rendered from {@code CitizenPromptViewFixture}.
 * See {@link PromptSnapshots} for how to regenerate them.
 */
class PromptSnapshotTest {
    static final String SAMPLE_SCRIPT = """
            ## Transcript
            Maria Silva: [cheerfully] Joao! The wheat is finally ready.

            Joao Costa: [grumbles] Good. Maybe now someone will fix my roof.
            """;

    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    @Test
    void playerRoleplay() {
        PromptSnapshots.assertMatches("player-roleplay",
                MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(citizen().build())));
    }

    @Test
    void systemControlledRoleplay() {
        PromptSnapshots.assertMatches("system-controlled-roleplay",
                MiscUtil.withFirstPicks(() -> PromptRuntime.generateSystemControlledRoleplayPrompt(
                        citizen().withoutPlayer().build())));
    }

    @Test
    void flashConversationScript() {
        PromptSnapshots.assertMatches("flash-conversation-script",
                MiscUtil.withFirstPicks(() -> renderFlash(flashRequest("English"))));
    }

    @Test
    void ttsRequest() {
        PromptSnapshots.assertMatches("tts-request", renderTts(ttsRequest("English")));
    }

    @Test
    void pregenerationGreeting() {
        CitizenPromptView view = citizen().withoutPlayer().build();
        PromptSnapshots.assertMatches("pregeneration-greeting", MiscUtil.withFirstPicks(() -> section("system instruction",
                PromptRuntime.generateSystemControlledRoleplayPrompt(view))
                + section("realtime input", pregenerationGreetingInput())));
    }

    @Test
    void addonTextCitizen() {
        PromptSnapshots.assertMatches("addon-text-citizen",
                MiscUtil.withFirstPicks(() -> TextPrompts.citizen(citizen().withoutPlayer().build())));
    }

    static GeminiFlash.GenerateContentRequest flashRequest(String language) {
        List<CitizenPromptView> views = List.of(
                citizen().withoutPlayer().language(language).build(),
                secondCitizen().withoutPlayer().language(language).build());
        return CitizenConversationGenerator.getFlashPrompt(
                CitizenConversationGenerator.participantInfo(views).toString(), language);
    }

    static GeminiTTS.RequestPayload ttsRequest(String language) {
        return CitizenConversationGenerator.getTTSPrompt(SAMPLE_SCRIPT, language, List.of(
                speaker("Maria Silva", "Kore"),
                speaker("Joao Costa", "Puck")));
    }

    static String pregenerationGreetingInput() {
        return PregenerationPrompts.withCacheSafetyNote(
                PregenerationPrompts.citizenGreeting("Joao Costa"), PregenerationKind.CITIZEN_GREETING);
    }

    static String renderFlash(GeminiFlash.GenerateContentRequest request) {
        return section("system instruction", joinFlashParts(request.system_instruction.parts))
                + section("contents", joinFlashParts(request.contents.parts));
    }

    static String renderTts(GeminiTTS.RequestPayload payload) {
        StringBuilder out = new StringBuilder();
        for (var content : payload.contents) {
            out.append(section("content (role=" + content.role + ")",
                    content.parts.stream().map(part -> part.text).collect(Collectors.joining("\n"))));
        }
        var config = payload.generationConfig;
        out.append(section("generation config", "responseModalities=" + config.responseModalities
                + "\ntemperature=" + config.temperature
                + "\nspeakers=" + config.speech_config.multi_speaker_voice_config.speaker_voice_configs.stream()
                .map(s -> s.speaker + ":" + s.voice_config.prebuilt_voice_config.voice_name)
                .collect(Collectors.joining(", "))));
        return out.toString();
    }

    private static String joinFlashParts(List<GeminiFlash.GenerateContentRequest.Part> parts) {
        return parts.stream().map(part -> part.text).collect(Collectors.joining("\n"));
    }

    private static String section(String title, String body) {
        return "===== " + title + " =====\n" + body + (body.endsWith("\n") ? "" : "\n") + "\n";
    }

    private static GeminiTTS.RequestPayload.SpeakerVoiceConfig speaker(String name, String voice) {
        var config = new GeminiTTS.RequestPayload.SpeakerVoiceConfig();
        config.speaker = name;
        config.voice_config = new GeminiTTS.RequestPayload.VoiceConfig();
        config.voice_config.prebuilt_voice_config = new GeminiTTS.RequestPayload.PrebuiltVoiceConfig();
        config.voice_config.prebuilt_voice_config.voice_name = voice;
        return config;
    }
}
