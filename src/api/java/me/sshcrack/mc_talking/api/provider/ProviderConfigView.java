package me.sshcrack.mc_talking.api.provider;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only configuration addons need, so they do not have to read the config class by reflection.
 *
 * @param apiKeySet                    whether a Gemini API key is configured (the key itself is never exposed)
 * @param liveModel                    provider model used for voice conversations
 * @param textModel                    provider model used for text generation, scripts and memory
 * @param blockingTaskUrgencyMultiplier urgent-contact weight added while a citizen's job is blocked
 */
public record ProviderConfigView(
        boolean apiKeySet,
        @NotNull String liveModel,
        @NotNull String textModel,
        double blockingTaskUrgencyMultiplier
) {
}
