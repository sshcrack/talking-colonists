package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintPrompts;
import me.sshcrack.mc_talking.conversations.complaints.ComplaintTopic;
import me.sshcrack.mc_talking.conversations.complaints.Complaints;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

/**
 * The citizen reports that it brought up one of its problems with the player, so it remembers how often
 * it asked. Works in every language, unlike matching words in what it said.
 */
public class RaiseConcernAction extends GeneralFunctionAction {
    public RaiseConcernAction() {
        super(ComplaintPrompts.TOOL_NAME,
                "Call this silently whenever you bring up one of your problems with the player you're talking to, "
                        + "so you remember that you told them. Never mention that you call it.",
                new ObjectProperty(new HashMap<>() {{
                    put("topic", new EnumProperty(Arrays.stream(ComplaintTopic.values()).map(ComplaintTopic::id).toList(), true));
                }}));
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        JsonObject result = new JsonObject();
        ComplaintTopic topic = parameters == null || !parameters.has("topic") ? null
                : ComplaintTopic.byId(parameters.get("topic").getAsString());
        UUID player = ConversationManager.getPlayerForEntity(citizen.getUUID());
        if (topic == null || player == null || citizen.getCitizenData() == null) {
            result.addProperty("success", false);
            return result;
        }
        MinecraftServer server = citizen.level().getServer();
        Runnable record = () -> Complaints.recordRaised(citizen.getCitizenData(), topic, player);
        if (server != null && !server.isSameThread()) server.execute(record);
        else record.run();
        result.addProperty("success", true);
        return result;
    }
}
