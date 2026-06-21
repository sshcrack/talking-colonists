package me.sshcrack.mc_talking.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.api.provider.AiRegistry;
import me.sshcrack.mc_talking.api.provider.BundledAiProvider;
import me.sshcrack.mc_talking.api.provider.LlmProvider;
import me.sshcrack.mc_talking.api.provider.PresetDefinition;
import me.sshcrack.mc_talking.api.provider.TtsProvider;
import me.sshcrack.mc_talking.api.provider.PregenerationProvider;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import org.jetbrains.annotations.Nullable;

public class ProviderUtil {
    private static final Gson GSON = new Gson();

    private ProviderUtil() {}

    @Nullable
    public static String resolveApiKey() {
        var config = McTalkingConfig.INSTANCE.instance();
        if (config.geminiApiKey != null && !config.geminiApiKey.isEmpty()) {
            return config.geminiApiKey;
        }
        if (config.providerConfig != null && !config.providerConfig.isBlank()) {
            try {
                var json = GSON.fromJson(config.providerConfig, JsonObject.class);
                if (json != null && json.has("gemini_live")) {
                    var geminiCfg = json.getAsJsonObject("gemini_live");
                    if (geminiCfg != null && geminiCfg.has("apiKey")) {
                        return geminiCfg.get("apiKey").getAsString();
                    }
                }
            } catch (Exception e) {
                McTalking.LOGGER.warn("Failed to parse providerConfig JSON", e);
            }
        }
        return null;
    }

    @Nullable
    public static PresetDefinition resolvePreset(String category) {
        var config = McTalkingConfig.INSTANCE.instance();
        String presetId;
        String fallbackId;

        switch (category) {
            case "live" -> {
                presetId = config.livePreset;
                fallbackId = config.liveFallback;
            }
            case "pregenerated" -> {
                presetId = config.pregeneratedPreset;
                fallbackId = config.pregeneratedFallback;
            }
            case "background" -> {
                presetId = config.backgroundPreset;
                fallbackId = config.backgroundFallback;
            }
            default -> {
                McTalking.LOGGER.warn("Unknown preset category: {}", category);
                return null;
            }
        }

        PresetDefinition preset = AiRegistry.getPreset(presetId);
        if (preset != null) return preset;

        if (fallbackId != null && !fallbackId.isEmpty()) {
            preset = AiRegistry.getPreset(fallbackId);
            if (preset != null) {
                McTalking.LOGGER.info("Falling back to preset: {} (primary: {} was unavailable)", fallbackId, presetId);
                return preset;
            }
        }

        McTalking.LOGGER.warn("No preset found for category {} (preset={}, fallback={})", category, presetId, fallbackId);
        return null;
    }

    @Nullable
    public static BundledAiProvider resolveBundledProvider(PresetDefinition preset) {
        if (preset.mode() != PresetDefinition.PresetMode.BUNDLED) return null;
        String providerId = preset.bundledProviderId();
        if (providerId == null) return null;
        var provider = AiRegistry.getProvider(providerId);
        if (provider instanceof BundledAiProvider bundled) return bundled;
        return null;
    }

    @Nullable
    public static LlmProvider resolveLlmProvider(PresetDefinition preset) {
        String providerId = preset.llmProviderId();
        if (providerId == null) return null;
        var provider = AiRegistry.getProvider(providerId);
        if (provider instanceof LlmProvider llm) return llm;
        return null;
    }

    @Nullable
    public static TtsProvider resolveTtsProvider(PresetDefinition preset) {
        String providerId = preset.ttsProviderId();
        if (providerId == null) return null;
        var provider = AiRegistry.getProvider(providerId);
        if (provider instanceof TtsProvider tts) return tts;
        return null;
    }

    @Nullable
    public static PregenerationProvider resolvePregenerationProvider(PresetDefinition preset) {
        String providerId = preset.pregenerationProviderId();
        if (providerId == null) return null;
        var provider = AiRegistry.getProvider(providerId);
        if (provider instanceof PregenerationProvider pregen) return pregen;
        return null;
    }
}
