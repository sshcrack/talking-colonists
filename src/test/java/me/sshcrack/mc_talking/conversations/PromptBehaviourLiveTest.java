package me.sshcrack.mc_talking.conversations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.api.prompt.view.BuildingView;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierType;
import me.sshcrack.mc_talking.api.prompt.view.HappinessModifierView;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.prompt.PromptRuntime;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import me.sshcrack.mc_talking.util.MiscUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static me.sshcrack.mc_talking.testing.CitizenPromptViewFixture.citizen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Opt-in checks of what the model actually does with our prompts. Runs only when
 * {@value #KEY_ENV} is set, which {@code scripts/test-prompt-behaviour.sh} does from the game
 * config; the normal test task never sets it. One request per scenario, no retries, small
 * output limits. The key is sent in a header and never logged.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = PromptBehaviourLiveTest.KEY_ENV, matches = ".+")
class PromptBehaviourLiveTest {
    static final String KEY_ENV = "MC_TALKING_PROMPT_BEHAVIOUR_KEY";
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private static final Set<String> PORTUGUESE_WORDS = Set.of(
            "não", "que", "você", "é", "está", "uma", "um", "para", "com", "muito", "bem", "eu", "de", "do",
            "da", "isso", "mas", "meu", "minha", "sim", "obrigado", "obrigada", "tudo", "aqui", "vamos");
    private static final Set<String> ENGLISH_WORDS = Set.of(
            "the", "and", "is", "you", "to", "of", "that", "it", "what", "my", "i", "we", "are", "this",
            "have", "with", "for", "yes", "not", "here");
    private static final List<String> STYLE_COMPLAINTS = List.of(
            "damp", "gloomy", "cramped", "dreary", "ugly", "depressing", "hate", "miserable", "dingy",
            "húmid", "escur");

    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    /** Configured Portuguese must produce a Portuguese citizen-to-citizen script. */
    @Test
    void portugueseConversationScript() {
        JsonObject request = GSON.toJsonTree(
                MiscUtil.withFirstPicks(() -> PromptSnapshotTest.flashRequest("Portuguese"))).getAsJsonObject();
        request.add("generationConfig", generationConfig(400));

        String script = text(generate(request));
        // Only the transcript must be Portuguese; profile/director notes may stay in English.
        int transcript = script.toLowerCase(Locale.ROOT).lastIndexOf("transcript");
        List<String> lines = new ArrayList<>();
        for (String line : script.substring(Math.max(transcript, 0)).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && colon < 40) lines.add(line.substring(colon + 1));
        }
        assertTrue(lines.size() >= 2, "expected transcript lines, got:\n" + script);

        String dialogue = String.join(" ", lines).replaceAll("\\[[^]]*]", " ");
        int portuguese = count(dialogue, PORTUGUESE_WORDS);
        int english = count(dialogue, ENGLISH_WORDS);
        assertTrue(portuguese >= 5 && portuguese > english * 2,
                "dialogue does not look Portuguese (pt=" + portuguese + ", en=" + english + "):\n" + script);
    }

    /** A well-kept home in a cavern-style building must not be criticised for its style. */
    @Test
    void cavernStyleHomeIsNotAComplaint() {
        String system = MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(citizen()
                .home(new BuildingView("Residence", 3))
                .happiness(8.5)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.HOMELESSNESS, 1.0),
                        new HappinessModifierView(HappinessModifierType.FOOD, 1.0))
                .build()));
        String reply = text(generate(request(system,
                "[Steve is now speaking to you] Your house was built in the cavern style. How do you like living there?",
                null, 200)));

        String lower = reply.toLowerCase(Locale.ROOT);
        List<String> complaints = STYLE_COMPLAINTS.stream().filter(lower::contains).toList();
        assertTrue(complaints.isEmpty(), "reply complains about the style " + complaints + ":\n" + reply);
    }

    /** A question a tool can answer must trigger the tool instead of an invented answer. */
    @Test
    void toolAnswerableQuestionCallsTheTool() {
        String system = MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(citizen().build()));
        JsonArray declarations = new JsonArray();
        declarations.add(function("list_citizens", "Listens all citizens in the colony by their names."));
        declarations.add(function("get_inventory", "Lists the current items in your inventory."));
        JsonObject tools = new JsonObject();
        tools.add("functionDeclarations", declarations);

        JsonObject response = generate(request(system,
                "[Steve is now speaking to you] Who else lives in our colony? Tell me all of their names.",
                tools, 200));

        String called = null;
        for (JsonElement part : parts(response)) {
            JsonObject call = part.getAsJsonObject().getAsJsonObject("functionCall");
            if (call != null) called = call.get("name").getAsString();
        }
        if (called == null) fail("expected a list_citizens call, got text:\n" + text(response));
        assertEquals("list_citizens", called);
    }

    private static JsonObject request(String system, String user, JsonObject tools, int maxOutputTokens) {
        JsonObject request = new JsonObject();
        request.add("system_instruction", content(null, system));
        JsonArray contents = new JsonArray();
        contents.add(content("user", user));
        request.add("contents", contents);
        if (tools != null) {
            JsonArray toolList = new JsonArray();
            toolList.add(tools);
            request.add("tools", toolList);
        }
        request.add("generationConfig", generationConfig(maxOutputTokens));
        return request;
    }

    private static JsonObject content(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        if (role != null) content.addProperty("role", role);
        content.add("parts", parts);
        return content;
    }

    private static JsonObject function(String name, String description) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        return function;
    }

    private static JsonObject generationConfig(int maxOutputTokens) {
        JsonObject config = new JsonObject();
        config.addProperty("maxOutputTokens", maxOutputTokens);
        config.addProperty("temperature", 0.4);
        return config;
    }

    private static JsonObject generate(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT.formatted(McTalkingConfig.FLASH_MODEL)))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", System.getenv(KEY_ENV))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new AssertionError("Gemini request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Gemini request interrupted", e);
        }
        // Quota is an environment problem, not a prompt regression: report it as skipped.
        Assumptions.assumeFalse(response.statusCode() == 429, "Gemini quota exhausted (HTTP 429)");
        if (response.statusCode() != 200) {
            fail("Gemini returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return GSON.fromJson(response.body(), JsonObject.class);
    }

    private static JsonArray parts(JsonObject response) {
        JsonArray candidates = response.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) fail("no candidates: " + response);
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || content.getAsJsonArray("parts") == null) fail("empty candidate: " + response);
        return content.getAsJsonArray("parts");
    }

    private static String text(JsonObject response) {
        StringBuilder text = new StringBuilder();
        for (JsonElement part : parts(response)) {
            JsonElement value = part.getAsJsonObject().get("text");
            if (value != null) text.append(value.getAsString());
        }
        return text.toString();
    }

    private static int count(String text, Set<String> words) {
        int hits = 0;
        for (String token : Pattern.compile("[^\\p{L}]+").split(text.toLowerCase(Locale.ROOT))) {
            if (words.contains(token)) hits++;
        }
        return hits;
    }
}
