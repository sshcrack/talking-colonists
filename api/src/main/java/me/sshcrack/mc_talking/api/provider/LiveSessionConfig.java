package me.sshcrack.mc_talking.api.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LiveSessionConfig {
    private final String systemPrompt;
    private final List<ToolDefinition> tools;
    private final String voice;
    private final String language;
    private final String resumptionHandle;
    private final String modelName;

    private LiveSessionConfig(Builder builder) {
        this.systemPrompt = Objects.requireNonNullElse(builder.systemPrompt, "");
        this.tools = List.copyOf(builder.tools);
        this.voice = builder.voice;
        this.language = Objects.requireNonNullElse(builder.language, "en-US");
        this.resumptionHandle = builder.resumptionHandle;
        this.modelName = builder.modelName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public List<ToolDefinition> tools() {
        return tools;
    }

    public String voice() {
        return voice;
    }

    public String language() {
        return language;
    }

    public String resumptionHandle() {
        return resumptionHandle;
    }

    public String modelName() {
        return modelName;
    }

    public static final class Builder {
        private String systemPrompt = "";
        private final List<ToolDefinition> tools = new ArrayList<>();
        private String voice;
        private String language = "en-US";
        private String resumptionHandle;
        private String modelName;

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = Objects.requireNonNullElse(systemPrompt, "");
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

        public Builder voice(String voice) {
            this.voice = voice;
            return this;
        }

        public Builder language(String language) {
            this.language = Objects.requireNonNullElse(language, "en-US");
            return this;
        }

        public Builder resumptionHandle(String resumptionHandle) {
            this.resumptionHandle = resumptionHandle;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public LiveSessionConfig build() {
            return new LiveSessionConfig(this);
        }
    }
}
