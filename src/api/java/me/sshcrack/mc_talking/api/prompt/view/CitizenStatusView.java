package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Structured visible citizen status with a stable Talking Colonists semantic type. */
public record CitizenStatusView(
        @NotNull CitizenStatusType type,
        @NotNull String translationKey,
        @NotNull String description,
        @NotNull List<String> contextValues
) {
    public CitizenStatusView {
        contextValues = List.copyOf(contextValues);
    }
}
