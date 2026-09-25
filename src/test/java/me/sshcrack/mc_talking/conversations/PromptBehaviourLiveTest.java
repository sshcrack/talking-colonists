package me.sshcrack.mc_talking.conversations;

import me.sshcrack.mc_talking.conversations.complaints.ComplaintContext;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintHistory;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintStage;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintTopic;
import me.sshcrack.mc_talking.testing.CitizenPromptViewFixture;
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
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Files;
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

    /**
     * A homeless citizen asked how they are: the first time constructive, after being ignored three
     * times openly fed up. Both replies go to {@code build/prompt-behaviour-complaints.txt} to read.
     */
    @Test
    void repeatedComplaintsSoundFedUp() throws IOException {
        String first = complaintReply(ComplaintStage.FIRST_MENTION, 0, null);
        String fedUp = complaintReply(ComplaintStage.FRUSTRATED, 3, null);
        Files.writeString(Path.of("build", "prompt-behaviour-complaints.txt"),
                "FIRST MENTION:\n" + first + "\n\nFRUSTRATED (raised 3 times, ignored):\n" + fedUp + "\n");

        String lower = fedUp.toLowerCase(Locale.ROOT);
        List<String> repetition = REPETITION.stream().filter(lower::contains).toList();
        assertTrue(!repetition.isEmpty(), "the fed-up reply should refer to having asked before:\n" + fedUp);
        String firstLower = first.toLowerCase(Locale.ROOT);
        assertTrue(REPETITION_ONLY.stream().noneMatch(firstLower::contains),
                "the first mention must not pretend it was said before:\n" + first);
    }

    /** Raising a problem is reported through raise_concern, so the history works in any language. */
    @Test
    void raisingAProblemCallsRaiseConcern() {
        JsonArray declarations = new JsonArray();
        JsonObject raise = function("raise_concern", "Call this silently whenever you bring up one of your problems "
                + "with the player you're talking to, so you remember that you told them. Never mention that you call it.");
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "OBJECT");
        JsonObject properties = new JsonObject();
        JsonObject topic = new JsonObject();
        topic.addProperty("type", "STRING");
        JsonArray topics = new JsonArray();
        for (ComplaintTopic value : ComplaintTopic.values()) topics.add(value.id());
        topic.add("enum", topics);
        properties.add("topic", topic);
        parameters.add("properties", properties);
        raise.add("parameters", parameters);
        declarations.add(raise);
        JsonObject tools = new JsonObject();
        tools.add("functionDeclarations", declarations);

        JsonObject response = generate(request(complaintPrompt(ComplaintStage.FIRST_MENTION, 0),
                "[Steve is now speaking to you] Hey, how are you holding up? Anything bothering you?", tools, 200));
        String calledTopic = null;
        for (JsonElement part : parts(response)) {
            JsonObject call = part.getAsJsonObject().getAsJsonObject("functionCall");
            if (call != null && "raise_concern".equals(call.get("name").getAsString())) {
                calledTopic = call.getAsJsonObject("args").get("topic").getAsString();
            }
        }
        if (calledTopic == null) fail("expected a raise_concern call, got text:\n" + text(response));
        assertEquals("housing", calledTopic);
    }

    private static final List<String> REPETITION = List.of(
            "again", "still", "already", "told you", "said", "times", "how many", "keep asking", "asked");
    private static final List<String> REPETITION_ONLY = List.of("told you", "how many times", "keep asking", "again and again");

    private static String complaintReply(ComplaintStage stage, int raised, JsonObject tools) {
        return text(generate(request(complaintPrompt(stage, raised),
                "[Steve is now speaking to you] Hey, how are you holding up?", tools, 200)));
    }

    private static String complaintPrompt(ComplaintStage stage, int raised) {
        var view = citizen()
                .happiness(3.5)
                .happinessModifiers(new HappinessModifierView(HappinessModifierType.HOMELESSNESS, 0.4, 9),
                        new HappinessModifierView(HappinessModifierType.FOOD, 1.0))
                .build();
        var note = new ComplaintHistory.Note(ComplaintTopic.HOUSING, stage, raised, 0, 2, false, false, false);
        return MiscUtil.withFirstPicks(() -> PromptRuntime.generateCitizenRoleplayPrompt(
                CitizenPromptViewFixture.withComplaints(view, new ComplaintContext(Map.of(ComplaintTopic.HOUSING, note), List.of()))));
    }

    /** A3: the real text runtime returns schema-valid JSON written in the configured language. */
    @Test
    void textGenerationReturnsStructuredPortuguese() {
        var runtime = new me.sshcrack.mc_talking.internal.text.TextGenerationRuntime(
                request -> me.sshcrack.gemini_live_lib.misc.GeminiFlash.sendFlashRequest(
                        McTalkingConfig.FLASH_MODEL, System.getenv(KEY_ENV), request, 1),
                new me.sshcrack.mc_talking.internal.text.TextGenerationRuntime.QuotaGate() {
                    @Override public boolean exhausted() { return false; }
                    @Override public void reportQuotaExceeded(Exception error) { }
                    @Override public void reportSuccess() { }
                },
                () -> true, Runnable::run);
        JsonObject schema = GSON.fromJson("""
                {"type": "object", "properties": {"headline": {"type": "string"}, "body": {"type": "string"}},
                 "required": ["headline", "body"]}""", JsonObject.class);
        String system = MiscUtil.withFirstPicks(() -> me.sshcrack.mc_talking.internal.text.TextPrompts.citizen(
                citizen().language("Portuguese").build()));

        var result = runtime.submit(system, me.sshcrack.mc_talking.api.text.TextRequest.of("live:gazette",
                "Write a short village newspaper item about the new bakery.").withResponseSchema(schema)).join();

        Assumptions.assumeFalse(result.status() == me.sshcrack.mc_talking.api.text.TextResult.Status.QUOTA, "quota exhausted");
        assertTrue(result.isSuccess(), "text generation failed: " + result);
        String body = result.json().get("headline").getAsString() + " " + result.json().get("body").getAsString();
        int portuguese = count(body, PORTUGUESE_WORDS);
        int english = count(body, ENGLISH_WORDS);
        assertTrue(portuguese >= 3 && portuguese > english * 2,
                "text does not look Portuguese (pt=" + portuguese + ", en=" + english + "): " + body);
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
