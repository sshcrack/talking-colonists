package me.sshcrack.mc_talking.api.voice;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record VoiceDescriptor(
    String id,
    String displayName,
    Gender gender,
    float pitchFactor,
    @Nullable String language,
    Map<String, JsonElement> extra
) {
}
