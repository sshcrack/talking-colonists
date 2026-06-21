package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.session.ToolCallHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public interface LlmProvider extends AiProvider {
    CompletableFuture<String> generate(String prompt, LlmConfig config);

    record LlmConfig(
        @Nullable String systemPrompt,
        @Nullable List<ToolDefinition> tools,
        @Nullable ToolCallHandler toolHandler,
        @Nullable String model,
        Map<String, JsonElement> extra
    ) {
        public LlmConfig {
            extra = Map.copyOf(extra);
        }
    }

    record ToolDefinition(
        String name,
        String description,
        @Nullable JsonObject parameters
    ) {}
}
