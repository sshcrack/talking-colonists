package me.sshcrack.mc_talking.manager.tools.specific;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.gson.properties.EnumProperty;
import me.sshcrack.mc_talking.gson.properties.ObjectProperty;
import me.sshcrack.mc_talking.gson.properties.PrimitiveProperty;
import me.sshcrack.mc_talking.manager.tools.FunctionAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

//TODO: Implement job-specific actions
public class JobSpecificAction extends FunctionAction {
    public JobSpecificAction() {
        super("job_action", "Performs a job-specific action for the citizen. To get available actions use 'list_job_actions'.",
                new ObjectProperty(new HashMap<>() {{
//                    put("job_action", new EnumProperty(JobActions.values(), true));
                }})
        );
    }

    @Override
    public @NotNull JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        return null;
    }
}
