package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.ai.JobStatus;
import me.sshcrack.mc_talking.api.prompt.view.AIWorkerState;
import me.sshcrack.mc_talking.api.prompt.view.BuilderActivityStatus;
import me.sshcrack.mc_talking.api.prompt.view.CitizenRequestAvailabilityView;
import me.sshcrack.mc_talking.api.prompt.view.ObservationState;
import me.sshcrack.mc_talking.api.prompt.view.ObservedValue;
import org.jetbrains.annotations.Nullable;

/** Pure classification of builder truth from one citizen-local snapshot. */
final class BuilderActivityClassifier {
    private BuilderActivityClassifier() {
    }

    static BuilderActivityStatus classify(
            boolean builder,
            boolean asleep,
            JobStatus jobStatus,
            @Nullable AIWorkerState workState,
            ObservedValue<CitizenRequestAvailabilityView> requests
    ) {
        if (!builder) return BuilderActivityStatus.NOT_BUILDER;
        if (asleep) return BuilderActivityStatus.SLEEPING;
        if (requests.state() == ObservationState.CURRENT && requests.value() != null) {
            var value = requests.value();
            if (!value.waitingForResolver().isEmpty() || !value.assignedOrInProgress().isEmpty()) {
                if (jobStatus == JobStatus.STUCK
                        || workState == AIWorkerState.NEEDS_ITEM
                        || workState == AIWorkerState.GATHERING_REQUIRED_MATERIALS) {
                    return BuilderActivityStatus.WAITING_FOR_MATERIALS;
                }
            }
        }
        return switch (jobStatus) {
            case WORKING -> BuilderActivityStatus.ACTIVE;
            case IDLE -> BuilderActivityStatus.IDLE;
            case STUCK -> BuilderActivityStatus.UNKNOWN;
        };
    }
}
