package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Current request-system view for this citizen. Empty lists with a CURRENT observation mean there
 * really are no open requests; unavailable request data is represented by the surrounding
 * {@link ObservedValue} instead.
 */
public record CitizenRequestAvailabilityView(
        @NotNull List<String> assignedOrInProgress,
        @NotNull List<String> waitingForResolver
) {
    public CitizenRequestAvailabilityView {
        assignedOrInProgress = List.copyOf(assignedOrInProgress);
        waitingForResolver = List.copyOf(waitingForResolver);
    }
}
