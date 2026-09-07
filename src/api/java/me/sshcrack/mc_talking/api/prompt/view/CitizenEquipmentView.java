package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Bounded snapshot of items actually carried/worn by this citizen. */
public record CitizenEquipmentView(
        @NotNull List<String> wornArmor,
        @NotNull List<String> carriedItems
) {
    public CitizenEquipmentView {
        wornArmor = List.copyOf(wornArmor);
        carriedItems = List.copyOf(carriedItems);
    }

    public boolean empty() {
        return wornArmor.isEmpty() && carriedItems.isEmpty();
    }
}
