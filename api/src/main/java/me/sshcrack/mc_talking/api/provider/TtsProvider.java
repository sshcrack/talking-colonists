package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.audio.AudioChunk;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;

public interface TtsProvider extends AiProvider {
    CompletableFuture<AudioChunk> synthesize(String text, TtsConfig config);

    record TtsConfig(
        @Nullable String voiceId,
        @Nullable String language,
        @Nullable String model,
        Map<String, JsonElement> extra
    ) {
        public TtsConfig {
            extra = Map.copyOf(extra);
        }
    }
}
