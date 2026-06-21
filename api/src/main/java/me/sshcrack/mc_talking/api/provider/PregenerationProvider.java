package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.audio.AudioChunk;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;

public interface PregenerationProvider extends AiProvider {
    CompletableFuture<PregeneratedConversation> generateConversation(
        ConversationScript script, PregenerationConfig config
    );

    record ConversationScript(List<DialogueLine> lines) {
        public ConversationScript {
            lines = List.copyOf(lines);
        }
    }

    record DialogueLine(String speakerName, String text, @Nullable String voiceId) {}

    record PregeneratedConversation(AudioChunk audio) {}

    record PregenerationConfig(@Nullable String model, Map<String, JsonElement> extra) {
        public PregenerationConfig {
            extra = Map.copyOf(extra);
        }
    }
}
