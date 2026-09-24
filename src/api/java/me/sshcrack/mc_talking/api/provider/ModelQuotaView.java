package me.sshcrack.mc_talking.api.provider;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Quota state of one provider model.
 *
 * @param model          the provider model name, or {@code "tts"} for speech synthesis
 * @param state          whether work can currently be scheduled on it
 * @param exhaustedUntil when the provider said the quota resets, if it said so; always null unless
 *                       {@code state} is {@link ProviderQuotaState#EXHAUSTED}. A null value while
 *                       exhausted means the reset time is unknown.
 */
public record ModelQuotaView(@NotNull String model, @NotNull ProviderQuotaState state, @Nullable Instant exhaustedUntil) {
    public ModelQuotaView {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(state, "state");
        if (state != ProviderQuotaState.EXHAUSTED) exhaustedUntil = null;
    }
}
