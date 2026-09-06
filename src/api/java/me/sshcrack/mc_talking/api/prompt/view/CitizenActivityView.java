package me.sshcrack.mc_talking.api.prompt.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Current citizen activity exposed entirely through stable Talking Colonists-owned semantic types.
 *
 * <p>The compatibility enums deliberately shield addons from MineColonies patch-level enum churn.
 * Unknown/new upstream states map to the relevant {@code UNKNOWN} value until Talking Colonists is
 * updated. Human-readable context remains available separately for presentation.</p>
 */
public record CitizenActivityView(
        @NotNull CitizenActivityCategory category,
        @Nullable CitizenStatusView status,
        @Nullable CitizenAIState citizenState,
        @Nullable AIWorkerState workState,
        @Nullable CitizenSubState subState,
        @Nullable String description,
        @Nullable String nameTagDescription,
        @NotNull List<String> recentActions
) {
    public CitizenActivityView {
        recentActions = List.copyOf(recentActions);
    }

    /** Convenience for work-state behavior without duplicating that property on this snapshot. */
    public boolean okayToEat() {
        return workState != null && workState.isOkayToEat();
    }
}
