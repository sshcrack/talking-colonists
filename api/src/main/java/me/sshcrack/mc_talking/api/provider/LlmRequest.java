package me.sshcrack.mc_talking.api.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LlmRequest {
    private final String systemPrompt;
    private final List<LlmMessage> history;
    private final String userText;
    private final List<ToolDefinition> tools;

    public LlmRequest(String systemPrompt, List<LlmMessage> history, String userText, List<ToolDefinition> tools) {
        this.systemPrompt = Objects.requireNonNullElse(systemPrompt, "");
        this.history = history != null ? List.copyOf(history) : List.of();
        this.userText = Objects.requireNonNullElse(userText, "");
        this.tools = tools != null ? List.copyOf(tools) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public List<LlmMessage> history() {
        return history;
    }

    public String userText() {
        return userText;
    }

    public List<ToolDefinition> tools() {
        return tools;
    }

    public static final class Builder {
        private String systemPrompt = "";
        private final List<LlmMessage> history = new ArrayList<>();
        private String userText = "";
        private final List<ToolDefinition> tools = new ArrayList<>();

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = Objects.requireNonNullElse(systemPrompt, "");
            return this;
        }

        public Builder addHistory(LlmMessage message) {
            this.history.add(message);
            return this;
        }

        public Builder history(List<LlmMessage> history) {
            this.history.clear();
            if (history != null) this.history.addAll(history);
            return this;
        }

        public Builder userText(String userText) {
            this.userText = Objects.requireNonNullElse(userText, "");
            return this;
        }

        public Builder addTool(ToolDefinition tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools.clear();
            if (tools != null) this.tools.addAll(tools);
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(systemPrompt, history, userText, tools);
        }
    }

    public static final class LlmMessage {
        private final Role role;
        private final String text;

        public LlmMessage(Role role, String text) {
            this.role = Objects.requireNonNull(role);
            this.text = Objects.requireNonNullElse(text, "");
        }

        public Role role() {
            return role;
        }

        public String text() {
            return text;
        }

        public enum Role {
            USER,
            ASSISTANT,
            SYSTEM
        }
    }
}
