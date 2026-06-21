package me.sshcrack.mc_talking.api.provider;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record PresetDefinition(
    String id,
    String displayName,
    @Nullable String sourceModId,
    PresetMode mode,
    @Nullable String bundledProviderId,
    @Nullable String sttProviderId,
    @Nullable String llmProviderId,
    @Nullable String ttsProviderId,
    @Nullable String pregenerationProviderId,
    Map<String, JsonElement> providerConfigs
) {
    public enum PresetMode {
        BUNDLED,
        PIPELINE
    }

    public String fullId() {
        return sourceModId != null ? sourceModId + "." + id : id;
    }

    public boolean supportsLive() {
        if (mode == PresetMode.BUNDLED) return bundledProviderId != null;
        return llmProviderId != null && ttsProviderId != null;
    }

    public boolean supportsPregenerated() {
        if (mode == PresetMode.BUNDLED) return bundledProviderId != null;
        return llmProviderId != null && ttsProviderId != null;
    }

    public boolean supportsBackground() {
        if (mode == PresetMode.BUNDLED) return bundledProviderId != null;
        return llmProviderId != null;
    }
}
