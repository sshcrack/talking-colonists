package me.sshcrack.mc_talking.api.provider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LlmResponse {
    private final String text;
    private final List<ToolCall> toolCalls;

    public LlmResponse(String text, List<ToolCall> toolCalls) {
        this.text = Objects.requireNonNullElse(text, "");
        this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public static LlmResponse ofText(String text) {
        return new LlmResponse(text, List.of());
    }

    public String text() {
        return text;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
