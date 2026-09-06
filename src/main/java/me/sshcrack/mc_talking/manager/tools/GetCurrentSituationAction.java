package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.prompt.view.CitizenActivityCategory;
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

        String activity = view.activity().description();
        if (activity != null) result.addProperty("current_activity", activity);

        String workStatus;
        if (!view.work().blockedItemRequests().isEmpty()) {
            workStatus = "STUCK";
        } else if (view.activity().category() == CitizenActivityCategory.WORKING) {
            workStatus = "WORKING";
        } else {
            workStatus = "IDLE";
        }
        result.addProperty("work_status", workStatus);

        if (!view.work().blockedItemRequests().isEmpty()) {
            result.add("blocked_item_requests", toJsonArray(view.work().blockedItemRequests()));
        }
        if (!view.work().fulfillableItemRequests().isEmpty()) {
            result.add("fulfillable_item_requests", toJsonArray(view.work().fulfillableItemRequests()));
        }

        result.addProperty("saturation_level_0_to_20", view.wellbeing().saturation());
        if (view.wellbeing().healthPercent() != null) {
            result.addProperty("health_percent", view.wellbeing().healthPercent());
        }

        if (view.work().jobName() != null) result.addProperty("job_name", view.work().jobName());
        if (!view.activity().recentActions().isEmpty()) {
            result.add("recent_actions", toJsonArray(view.activity().recentActions()));
        }

        return result;
    }

    private static JsonArray toJsonArray(List<String> items) {
        var arr = new JsonArray();
        for (String item : items) arr.add(item);
        return arr;
    }
}
