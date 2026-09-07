package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.prompt.view.CitizenVerifiedFactsView;
import me.sshcrack.mc_talking.api.prompt.view.ObservationState;

import java.util.Locale;

/** Pure renderer kept separate so verified-fact precedence and unknown/empty semantics are testable. */
final class VerifiedFactPromptRenderer {
    private VerifiedFactPromptRenderer() {
    }

    static String render(CitizenVerifiedFactsView verified) {
        StringBuilder out = new StringBuilder();
        out.append("## VERIFIED CURRENT FACTS\n");
        out.append("- These point-in-time observations override contradictory recollections below. ")
                .append("UNLOADED/UNAVAILABLE means unknown, not zero/empty or the opposite state.\n");

        switch (verified.housingStatus()) {
            case HOUSED -> out.append("- Housing: currently assigned a home. Do not claim to be homeless.\n");
            case HOMELESS -> out.append("- Housing: currently no home is assigned.\n");
            case UNKNOWN -> out.append("- Housing: current assignment could not be verified; do not infer housed or homeless.\n");
        }

        var health = verified.healthPercent();
        if (health.state() == ObservationState.CURRENT) {
            out.append("- Health: ").append(String.format(Locale.ROOT, "%.1f", health.value())).append("% now.\n");
        } else {
            out.append("- Health: ").append(health.state()).append("; current HP is unknown.\n");
        }

        var equipment = verified.equipment();
        if (equipment.state() == ObservationState.CURRENT && equipment.value() != null) {
            if (equipment.value().empty()) {
                out.append("- Equipment/inventory: observed empty right now.\n");
            } else {
                if (!equipment.value().wornArmor().isEmpty()) {
                    out.append("- Worn armor now: ").append(String.join(", ", equipment.value().wornArmor())).append(".\n");
                }
                if (!equipment.value().carriedItems().isEmpty()) {
                    out.append("- Carried items now: ").append(String.join(", ", equipment.value().carriedItems())).append(".\n");
                }
            }
        } else {
            out.append("- Equipment/inventory: ").append(equipment.state())
                    .append("; do not infer empty or equipped.\n");
        }

        out.append("- Builder activity: ").append(verified.builderActivity()).append(".\n");

        var requests = verified.requests();
        if (requests.state() == ObservationState.CURRENT && requests.value() != null) {
            if (requests.value().waitingForResolver().isEmpty()
                    && requests.value().assignedOrInProgress().isEmpty()) {
                out.append("- Work requests: no open requests in this snapshot.\n");
            }
            if (!requests.value().waitingForResolver().isEmpty()) {
                out.append("- Work requests waiting for a resolver: ")
                        .append(String.join(", ", requests.value().waitingForResolver()))
                        .append(". This does NOT prove colony/warehouse stock is empty.\n");
            }
            if (!requests.value().assignedOrInProgress().isEmpty()) {
                out.append("- Work requests assigned/in progress: ")
                        .append(String.join(", ", requests.value().assignedOrInProgress()))
                        .append(". This does NOT prove the items are already in your inventory.\n");
            }
        } else {
            out.append("- Work requests: ").append(requests.state())
                    .append("; do not infer there are zero requests.\n");
        }
        return out.toString();
    }
}
