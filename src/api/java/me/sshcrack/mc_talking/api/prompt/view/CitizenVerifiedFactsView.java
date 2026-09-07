package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

/**
 * Point-in-time authoritative facts used ahead of recollections in prompts.
 *
 * <p>These values are snapshots, not subscriptions. Actions that depend on them should refresh the
 * citizen context immediately before executing.</p>
 */
public record CitizenVerifiedFactsView(
        long capturedAtGameTime,
        @NotNull ObservedValue<Double> healthPercent,
        @NotNull ObservedValue<CitizenEquipmentView> equipment,
        @NotNull CitizenHousingStatus housingStatus,
        @NotNull ObservedValue<CitizenRequestAvailabilityView> requests,
        @NotNull BuilderActivityStatus builderActivity
) {
}
