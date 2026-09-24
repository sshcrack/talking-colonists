package me.sshcrack.mc_talking.api.provider;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable point-in-time view of provider capacity and quota.
 *
 * @param foreground  slots for audible conversations (player, ambient, controlled turns)
 * @param background  slots for background work such as greetings and memory compaction
 * @param models      quota of the Live model, the text model and {@code "tts"}
 * @param capturedAt  when this view was taken
 */
public record ProviderBudgetView(
        @NotNull SlotUsage foreground,
        @NotNull SlotUsage background,
        @NotNull List<ModelQuotaView> models,
        @NotNull Instant capturedAt
) {
    public ProviderBudgetView {
        Objects.requireNonNull(foreground, "foreground");
        Objects.requireNonNull(background, "background");
        models = List.copyOf(models);
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public @NotNull Optional<ModelQuotaView> model(@NotNull String model) {
        return models.stream().filter(view -> view.model().equals(model)).findFirst();
    }

    /** Whether every tracked model is usable and at least one foreground slot is free. */
    public boolean canStartConversation() {
        return foreground.available() > 0 && models.stream().noneMatch(m -> m.state() != ProviderQuotaState.OK);
    }
}
