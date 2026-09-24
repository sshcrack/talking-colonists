package me.sshcrack.mc_talking.conversations;

import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import me.sshcrack.mc_talking.util.MiscUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static me.sshcrack.mc_talking.testing.CitizenPromptViewFixture.citizen;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rules every prompt path must keep, independent of the exact snapshot wording. */
class PromptInvariantTest {
    private static final String LANGUAGE = "Portuguese";

    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    /** Every prompt that makes a model speak or write dialogue, rendered with a non-English language. */
    private static Map<String, String> speechPrompts() {
        Map<String, Supplier<String>> paths = new LinkedHashMap<>();
        paths.put("player roleplay", () -> PromptRuntime.generateCitizenRoleplayPrompt(
                citizen().language(LANGUAGE).build()));
        paths.put("system-controlled roleplay / pregeneration", () -> PromptRuntime.generateSystemControlledRoleplayPrompt(
                citizen().withoutPlayer().language(LANGUAGE).build()));
        paths.put("flash conversation script", () -> PromptSnapshotTest.flashRequest(LANGUAGE).system_instruction.parts.get(0).text);
        paths.put("tts request", () -> PromptSnapshotTest.ttsRequest(LANGUAGE).contents.get(0).parts.get(0).text);

        Map<String, String> rendered = new LinkedHashMap<>();
        paths.forEach((name, prompt) -> rendered.put(name, MiscUtil.withFirstPicks(prompt)));
        return rendered;
    }

    @Test
    void configuredLanguageReachesEverySpeechPrompt() {
        assertAll(speechPrompts().entrySet().stream().map(entry -> () ->
                assertTrue(entry.getValue().contains(LANGUAGE),
                        entry.getKey() + " does not name the configured language " + LANGUAGE)));
    }

    @Test
    void housingSectionSaysBuildingStyleIsNotAComplaint() {
        String prompt = MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(citizen().build()));
        String info = MiscUtil.withFirstPicks(() -> PromptRuntime.generateConversationalInfoPrompt(citizen().build()));

        assertAll(
                () -> assertTrue(prompt.contains("is NOT a sign of poor quality"), "roleplay prompt"),
                () -> assertTrue(info.contains("is NOT a sign of poor quality"), "conversational info prompt"));
    }

    @Test
    void liveRoleplayPromptsForbidMarkdown() {
        String player = MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(citizen().build()));
        String system = MiscUtil.withFirstPicks(() -> PromptRuntime.generateSystemControlledRoleplayPrompt(
                citizen().withoutPlayer().build()));

        assertAll(
                () -> assertTrue(player.contains("Do not use markdown"), "player roleplay"),
                () -> assertTrue(system.contains("Do not use markdown"), "system-controlled roleplay"),
                () -> assertFalse(player.contains("plain text.-"), "guideline lines must not run together"));
    }
}
