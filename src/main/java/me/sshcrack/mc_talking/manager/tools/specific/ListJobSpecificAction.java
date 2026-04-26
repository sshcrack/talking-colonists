package me.sshcrack.mc_talking.manager.tools.specific;


import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.manager.tools.FunctionAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ListJobSpecificAction extends FunctionAction {
    public ListJobSpecificAction() {
        super("list_job_actions", "Lists all available job-specific actions for the citizen. " +
                "To perform a job-specific action use 'job_action'.");
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        return null;
    }
}
