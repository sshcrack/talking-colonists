package me.sshcrack.mc_talking.internal.text;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.api.prompt.view.ColonyPromptView;
import me.sshcrack.mc_talking.api.text.TextRequest;
import me.sshcrack.mc_talking.api.text.TextResult;
import me.sshcrack.mc_talking.testing.CitizenPromptViewFixture;
import me.sshcrack.mc_talking.testing.TestPromptProviders;
import me.sshcrack.mc_talking.util.MiscUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextGenerationRuntimeTest {
    private final List<GeminiFlash.GenerateContentRequest> sent = new ArrayList<>();
    private final AtomicBoolean quotaExhausted = new AtomicBoolean();
    private final AtomicInteger quotaReports = new AtomicInteger();
    private final ManualExecutor executor = new ManualExecutor();
    private String reply = "The harvest festival starts at dusk.";
    private Exception failure;

    private final TextGenerationRuntime runtime = new TextGenerationRuntime(
            request -> {
                sent.add(request);
                if (failure != null) throw failure;
                return reply;
            },
            new TextGenerationRuntime.QuotaGate() {
                @Override public boolean exhausted() { return quotaExhausted.get(); }
                @Override public void reportQuotaExceeded(Exception error) { quotaReports.incrementAndGet(); }
                @Override public void reportSuccess() { }
            },
            () -> true,
            executor);

    @BeforeAll
    static void installDefaultProvider() {
        TestPromptProviders.installDefault();
    }

    @Test
    void plainTextSucceedsAndCarriesDirectiveAndLength() {
        var future = runtime.submit("system", TextRequest.of("test:notice", "Write a notice").withMaxChars(200));
        executor.runAll();

        TextResult result = future.join();
        assertTrue(result.isSuccess());
        assertEquals("The harvest festival starts at dusk.", result.text());
        assertNull(result.json());
        String user = sent.get(0).contents.parts.get(0).text;
        assertTrue(user.startsWith("Write a notice"), user);
        assertTrue(user.contains("Keep it under 200 characters."), user);
        assertEquals("system", sent.get(0).system_instruction.parts.get(0).text);
    }

    @Test
    void citizenPromptPinsTheConfiguredLanguage() {
        String prompt = MiscUtil.withFirstPicks(() -> TextPrompts.citizen(
                CitizenPromptViewFixture.citizen().language("Portuguese").build()));

        assertTrue(prompt.contains("Write in Portuguese"), prompt);
        assertTrue(prompt.contains("You are Maria Silva."), prompt);
        assertTrue(prompt.contains("# CITIZEN INFO Maria Silva"), "uses the normal citizen context");
    }

    @Test
    void colonyPromptUsesOnlyColonyContextAndLanguage() {
        var colony = new ColonyPromptView(1, "Riverside", false, "Steve", 12, null, 0, 0L,
                List.of("A new bakery was built"), List.of(), null, null);
        String prompt = TextPrompts.colony(colony, "German");

        assertTrue(prompt.contains("on behalf of the colony Riverside as a whole"), prompt);
        assertTrue(prompt.contains("- A new bakery was built"), prompt);
        assertTrue(prompt.contains("Write in German"), prompt);
    }

    @Test
    void structuredOutputIsParsedAndValidated() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonArray required = new JsonArray();
        required.add("headline");
        schema.add("required", required);
        TextRequest request = TextRequest.of("gazette:headline", "Write a headline").withResponseSchema(schema);

        reply = "```json\n{\"headline\": \"Bakery opens\"}\n```";
        var ok = runtime.submit("system", request);
        executor.runAll();
        assertTrue(ok.join().isSuccess());
        assertEquals("Bakery opens", ok.join().json().get("headline").getAsString());
        assertEquals("application/json", sent.get(0).generationConfig.responseMimeType);

        reply = "{\"title\": \"Bakery opens\"}";
        var missing = runtime.submit("system", request);
        executor.runAll();
        assertEquals(TextResult.Status.INVALID_OUTPUT, missing.join().status());
        assertTrue(missing.join().detail().contains("headline"));

        reply = "Bakery opens!";
        var notJson = runtime.submit("system", request);
        executor.runAll();
        assertEquals(TextResult.Status.INVALID_OUTPUT, notJson.join().status());
    }

    @Test
    void exhaustedQuotaFailsFastAndProviderQuotaErrorsAreReported() {
        quotaExhausted.set(true);
        assertEquals(TextResult.Status.QUOTA, runtime.submit("system", TextRequest.of("t", "Hi")).join().status());
        assertTrue(sent.isEmpty(), "no request while the quota is exhausted");

        quotaExhausted.set(false);
        failure = new UnexpectedResponseException("RESOURCE_EXHAUSTED", 429, "{}");
        var future = runtime.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();
        assertEquals(TextResult.Status.QUOTA, future.join().status());
        assertEquals(1, quotaReports.get());
    }

    @Test
    void otherProviderErrorsAreTypedWithoutLeakingDetails() {
        failure = new java.io.IOException("connect failed https://example?key=SECRET");
        var future = runtime.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();

        assertEquals(TextResult.Status.PROVIDER_ERROR, future.join().status());
        assertFalse(future.join().detail().contains("SECRET"));
    }

    private final List<String[]> liveSent = new ArrayList<>();
    private final AtomicBoolean liveAvailable = new AtomicBoolean(true);
    private String liveReply = "The festival starts at dusk, friend.";
    private Exception liveFailure;

    private final TextGenerationRuntime withFallback = new TextGenerationRuntime(
            request -> {
                sent.add(request);
                if (failure != null) throw failure;
                return reply;
            },
            new TextGenerationRuntime.Fallback() {
                @Override public boolean available() { return liveAvailable.get(); }

                @Override
                public String send(String systemPrompt, String userText) throws Exception {
                    liveSent.add(new String[]{systemPrompt, userText});
                    if (liveFailure != null) throw liveFailure;
                    return liveReply;
                }
            },
            new TextGenerationRuntime.QuotaGate() {
                @Override public boolean exhausted() { return quotaExhausted.get(); }
                @Override public void reportQuotaExceeded(Exception error) { quotaReports.incrementAndGet(); }
                @Override public void reportSuccess() { }
            },
            () -> true,
            executor);

    @Test
    void exhaustedFlashQuotaUsesTheLiveFallbackWithTheSameRequest() {
        quotaExhausted.set(true);
        var future = withFallback.submit("system", TextRequest.of("t", "Write a notice").withMaxChars(200));
        executor.runAll();

        assertTrue(future.join().isSuccess());
        assertEquals("The festival starts at dusk, friend.", future.join().text());
        assertTrue(sent.isEmpty(), "Flash is skipped while its quota is exhausted");
        assertEquals("system", liveSent.get(0)[0]);
        assertTrue(liveSent.get(0)[1].startsWith("Write a notice") && liveSent.get(0)[1].contains("Keep it under 200"));
    }

    @Test
    void flashQuotaAndServerErrorsFallBackButBadRequestsDoNot() {
        failure = new UnexpectedResponseException("RESOURCE_EXHAUSTED", 429, "{}");
        var quota = withFallback.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();
        assertTrue(quota.join().isSuccess());
        assertEquals(1, quotaReports.get());

        failure = new UnexpectedResponseException("unavailable", 503, "{}");
        var server = withFallback.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();
        assertTrue(server.join().isSuccess());

        failure = new UnexpectedResponseException("bad request", 400, "{}");
        var bad = withFallback.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();
        assertEquals(TextResult.Status.PROVIDER_ERROR, bad.join().status());
        assertEquals(2, liveSent.size());
    }

    @Test
    void structuredFallbackPutsTheSchemaInThePromptAndStillValidates() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonArray required = new JsonArray();
        required.add("headline");
        schema.add("required", required);
        quotaExhausted.set(true);

        liveReply = "{\"headline\": \"Bakery opens\"}";
        var ok = withFallback.submit("system", TextRequest.of("t", "Headline").withResponseSchema(schema));
        executor.runAll();
        assertEquals("Bakery opens", ok.join().json().get("headline").getAsString());
        assertTrue(liveSent.get(0)[0].startsWith("system") && liveSent.get(0)[0].contains("\"headline\""),
                "the schema is part of the Live system prompt");

        liveReply = "Bakery opens!";
        var invalid = withFallback.submit("system", TextRequest.of("t", "Headline").withResponseSchema(schema));
        executor.runAll();
        assertEquals(TextResult.Status.INVALID_OUTPUT, invalid.join().status());
    }

    @Test
    void unavailableOrFailingFallbackKeepsTheFlashStatus() {
        quotaExhausted.set(true);
        liveAvailable.set(false);
        assertEquals(TextResult.Status.QUOTA, withFallback.submit("system", TextRequest.of("t", "Hi")).join().status());
        assertTrue(liveSent.isEmpty());

        liveAvailable.set(true);
        liveFailure = new IllegalStateException("closed early");
        var failed = withFallback.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();
        assertEquals(TextResult.Status.QUOTA, failed.join().status());

        quotaExhausted.set(false);
        failure = new java.io.IOException("connect failed");
        var network = withFallback.submit("system", TextRequest.of("t", "Hi"));
        executor.runAll();
        assertEquals(TextResult.Status.PROVIDER_ERROR, network.join().status());
    }

    @Test
    void concurrencyIsBoundedAndSlotsAreReleased() {
        var first = runtime.submit("system", TextRequest.of("t", "One"));
        var second = runtime.submit("system", TextRequest.of("t", "Two"));
        assertEquals(TextResult.Status.NO_CAPACITY, runtime.submit("system", TextRequest.of("t", "Three")).join().status());

        executor.runOne();
        assertTrue(first.join().isSuccess());
        var fourth = runtime.submit("system", TextRequest.of("t", "Four"));
        executor.runAll();
        assertTrue(second.join().isSuccess());
        assertTrue(fourth.join().isSuccess());
    }

    @Test
    void cancellationSkipsTheProviderAndFreesTheSlot() {
        var cancelledByCaller = runtime.submit("system", TextRequest.of("t", "One"));
        cancelledByCaller.cancel(false);
        var pending = runtime.submit("system", TextRequest.of("t", "Two"));

        runtime.cancelAll("The server is stopping");
        assertEquals(TextResult.Status.CANCELLED, pending.join().status());
        executor.runAll();
        assertTrue(sent.isEmpty(), "cancelled requests must not reach the provider");
        assertEquals(0, runtime.pendingCount());

        var after = runtime.submit("system", TextRequest.of("t", "Three"));
        var afterToo = runtime.submit("system", TextRequest.of("t", "Four"));
        executor.runAll();
        assertTrue(after.join().isSuccess() && afterToo.join().isSuccess(), "both slots were released");
    }

    @Test
    void missingKeyIsUnavailable() {
        var noKey = new TextGenerationRuntime(request -> "x", new TextGenerationRuntime.QuotaGate() {
            @Override public boolean exhausted() { return false; }
            @Override public void reportQuotaExceeded(Exception error) { }
            @Override public void reportSuccess() { }
        }, () -> false, Runnable::run);
        assertEquals(TextResult.Status.UNAVAILABLE, noKey.submit("system", TextRequest.of("t", "Hi")).join().status());
    }

    @Test
    void longTextIsTrimmedAtASentenceEnd() {
        assertEquals("One two. Three four.", TextGenerationRuntime.trim("One two. Three four. Five six seven eight.", 24));
        assertEquals("Short.", TextGenerationRuntime.trim("Short.", 24));
    }

    @Test
    void requestsValidateTheirInput() {
        assertThrows(IllegalArgumentException.class, () -> TextRequest.of("Bad Purpose", "Hi"));
        assertThrows(IllegalArgumentException.class, () -> TextRequest.of("ok", "  "));
        assertThrows(IllegalArgumentException.class, () -> TextRequest.of("ok", "Hi").withMaxChars(0));
        assertThrows(IllegalArgumentException.class, () -> TextResult.failure(TextResult.Status.SUCCESS, null));
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runOne() {
            tasks.remove().run();
        }

        void runAll() {
            while (!tasks.isEmpty()) runOne();
        }
    }
}
