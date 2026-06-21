package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.session.BundledSession;
import me.sshcrack.mc_talking.api.voice.VoiceDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;

public interface BundledAiProvider extends AiProvider {
    BundledSession createSession(SessionConfig config);

    record SessionConfig(
        @Nullable String systemPrompt,
        @Nullable VoiceDescriptor voice,
        boolean enableAudio,
        int maxOutputTokens,
        @Nullable String model,
        Map<String, JsonElement> extra
    ) {
        public SessionConfig {
            extra = Map.copyOf(extra);
        }
    }
}
