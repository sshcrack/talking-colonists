package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Family relationships known to Talking Colonists. */
public record CitizenFamilyView(
        @NotNull List<String> parentNames,
        boolean hasPartner,
        @NotNull List<String> childNames,
        @NotNull List<String> siblingNames
) {
    public CitizenFamilyView {
        parentNames = List.copyOf(parentNames);
        childNames = List.copyOf(childNames);
        siblingNames = List.copyOf(siblingNames);
    }
}
