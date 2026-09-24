package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryTextRequestTest {
    private final List<String> calls = new ArrayList<>();
    private boolean flashExhausted;
    private boolean liveExhausted;
    private Long reportedRetry = -1L;

    private final MemoryTextRequest.Quota quota = new MemoryTextRequest.Quota() {
        @Override public boolean flashExhausted() { return flashExhausted; }
        @Override public boolean liveExhausted() { return liveExhausted; }
        @Override public void flashExceeded(Long retryAfterMillis) { calls.add("flash-exceeded"); reportedRetry = retryAfterMillis; }
        @Override public void flashSucceeded() { calls.add("flash-ok"); }
    };

    private final MemoryTextRequest.Live live = () -> {
        calls.add("live");
        return "{\"from\":\"live\"}";
    };

    @Test
    void flashAnswerIsUsedWhenItWorks() throws Exception {
        String text = MemoryTextRequest.run(() -> "{\"from\":\"flash\"}", live, quota, "[test]");
        assertEquals("{\"from\":\"flash\"}", text);
        assertEquals(List.of("flash-ok"), calls);
    }

    @Test
    void usedUpFlashQuotaGoesStraightToLive() throws Exception {
        flashExhausted = true;
        String text = MemoryTextRequest.run(() -> { throw new AssertionError("Flash must not be called"); }, live, quota, "[test]");
        assertEquals("{\"from\":\"live\"}", text);
        assertEquals(List.of("live"), calls);
    }

    @Test
    void flash429IsRecordedAndFallsBackToLive() throws Exception {
        String text = MemoryTextRequest.run(() -> {
            throw new UnexpectedResponseException("quota", 429, "{}");
        }, live, quota, "[test]");
        assertEquals("{\"from\":\"live\"}", text);
        assertEquals(List.of("flash-exceeded", "live"), calls);
    }

    @Test
    void serverErrorsAndNetworkFailuresFallBack() throws Exception {
        assertEquals("{\"from\":\"live\"}", MemoryTextRequest.run(() -> {
            throw new UnexpectedResponseException("down", 503, "");
        }, live, quota, "[test]"));
        assertEquals("{\"from\":\"live\"}", MemoryTextRequest.run(() -> {
            throw new IOException("reset");
        }, live, quota, "[test]"));
    }

    @Test
    void clientErrorsDoNotFallBack() {
        assertThrows(MemoryTextRequest.FailedException.class, () -> MemoryTextRequest.run(() -> {
            throw new UnexpectedResponseException("bad key", 400, "");
        }, live, quota, "[test]"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void disabledFallbackFails() {
        flashExhausted = true;
        assertThrows(MemoryTextRequest.FailedException.class, () -> MemoryTextRequest.run(() -> "unused", null, quota, "[test]"));
    }

    @Test
    void bothModelsOutOfQuotaFails() {
        flashExhausted = true;
        liveExhausted = true;
        assertThrows(MemoryTextRequest.FailedException.class, () -> MemoryTextRequest.run(() -> "unused", live, quota, "[test]"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void liveSystemPromptCarriesTheSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        String prompt = MemoryTextRequest.liveSystemPrompt("Extract memories.", schema);
        assertTrue(prompt.startsWith("Extract memories."));
        assertTrue(prompt.contains("only one JSON object"));
        assertTrue(prompt.endsWith("{\"type\":\"object\"}"));
    }
}
