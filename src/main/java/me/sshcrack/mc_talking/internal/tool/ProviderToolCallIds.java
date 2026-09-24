package me.sshcrack.mc_talking.internal.tool;

import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.function.BiConsumer;

/**
 * Pairs the Gemini library's per-call {@code onFunctionCall(name, args)} callbacks with the provider's
 * call IDs, which the library does not pass through. The IDs of one {@code toolCall} message are read
 * before the library handles it and handed out in order, one per callback, on that same thread. The
 * ID is the idempotency key for addon command tools, so a batched built-in call must not shift it.
 */
public final class ProviderToolCallIds {
    record Call(String name, String id) {
    }

    private final ThreadLocal<ArrayDeque<Call>> active = new ThreadLocal<>();

    /**
     * Runs {@code handler} (the library's message handling) with the call IDs of {@code message}
     * available to {@link #poll}. Messages without tool calls run with nothing to poll.
     */
    public void handle(String message, Runnable handler) {
        ArrayDeque<Call> calls = parse(message);
        if (calls.isEmpty()) {
            handler.run();
            return;
        }
        active.set(calls);
        try {
            handler.run();
        } finally {
            calls.clear();
            active.remove();
        }
    }

    /**
     * The ID of the next call of the current message, or {@code ""} when there is none or the next
     * call has another name ({@code onMismatch} gets the expected and received names).
     */
    public String poll(String functionName, BiConsumer<String, String> onMismatch) {
        ArrayDeque<Call> calls = active.get();
        if (calls == null) return "";
        Call call = calls.pollFirst();
        if (call == null) return "";
        if (!call.name().equals(functionName)) {
            onMismatch.accept(call.name(), functionName);
            return "";
        }
        return call.id();
    }

    /** The function calls of a provider {@code toolCall} message in order; empty for anything else. */
    static ArrayDeque<Call> parse(String message) {
        ArrayDeque<Call> calls = new ArrayDeque<>();
        try {
            var parsed = JsonParser.parseString(message);
            if (!parsed.isJsonObject()) return calls;
            var outer = parsed.getAsJsonObject();
            if (!outer.has("toolCall") || !outer.get("toolCall").isJsonObject()) return calls;
            var toolCall = outer.getAsJsonObject("toolCall");
            if (!toolCall.has("functionCalls") || !toolCall.get("functionCalls").isJsonArray()) return calls;
            for (var element : toolCall.getAsJsonArray("functionCalls")) {
                if (!element.isJsonObject()) continue;
                var function = element.getAsJsonObject();
                if (!function.has("name") || !function.get("name").isJsonPrimitive()) continue;
                String id = function.has("id") && function.get("id").isJsonPrimitive()
                        ? function.get("id").getAsString()
                        : "";
                calls.addLast(new Call(function.get("name").getAsString(), id));
            }
        } catch (RuntimeException ignored) {
            // The Gemini library remains authoritative for malformed provider-message handling.
        }
        return calls;
    }
}
