package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.CitizenAIState;
import me.sshcrack.mc_talking.manager.AIStateDescriber;
import me.sshcrack.mc_talking.manager.CitizenPromptViewFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;

public class GetCurrentSituationAction extends GeneralFunctionAction {

    public GetCurrentSituationAction() {
        super(
                "get_current_situation",
                "Refreshes your knowledge of your own current situation — what you are doing right now, "
                + "what items you are waiting for, and your current status. "
                + "Call this when you suspect something may have changed since the start of the conversation "
                + "(e.g. the player tells you your supplies have arrived, or asks why you are not working)."
        );
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, JsonObject parameters) {
        var data = citizen.getCitizenData();
        if (data == null) {
            var err = new JsonObject();
            err.addProperty("error", "Citizen data not available.");
            return err;
        }

        var view = CitizenPromptViewFactory.create(data, new HashMap<>(), null);
        var result = new JsonObject();

        // Current activity
        String activity = AIStateDescriber.describeAiState(
                view.citizenAiState(), view.workAiState(), view.nameTagDescription(), view);
        if (activity != null) {
            result.addProperty("current_activity", activity);
        }

        // Work status
        String workStatus;
        if (view.blockedItemRequests() != null && !view.blockedItemRequests().isEmpty()) {
            workStatus = "STUCK";
        } else if (view.citizenAiState() == CitizenAIState.WORKING
                || view.citizenAiState() == CitizenAIState.WORK) {
            workStatus = "WORKING";
        } else {
            workStatus = "IDLE";
        }
        result.addProperty("work_status", workStatus);

        // Blocked item requests — must mention urgently
        if (view.blockedItemRequests() != null && !view.blockedItemRequests().isEmpty()) {
            result.add("blocked_item_requests", toJsonArray(view.blockedItemRequests()));
        }

        // Fulfillable item requests — system is handling them
        if (view.fulfillableItemRequests() != null && !view.fulfillableItemRequests().isEmpty()) {
            result.add("fulfillable_item_requests", toJsonArray(view.fulfillableItemRequests()));
        }

        // Saturation and health
        result.addProperty("saturation_level_0_to_20", view.saturation());
        if (view.healthPercent() != null) {
            result.addProperty("health_percent", view.healthPercent());
        }

        // Job
        if (view.jobName() != null) {
            result.addProperty("job_name", view.jobName());
        }

        // Recent actions
        if (view.recentActions() != null && !view.recentActions().isEmpty()) {
            result.add("recent_actions", toJsonArray(view.recentActions()));
        }

        return result;
    }

    private static JsonArray toJsonArray(List<String> items) {
        var arr = new JsonArray();
        for (String item : items) {
            arr.add(item);
        }
        return arr;
    }
}
