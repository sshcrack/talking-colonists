package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import me.sshcrack.mc_talking.broadcast.ColonyBroadcast;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;

public class InitiateBroadcastAction extends PlayerFunctionAction {
    public InitiateBroadcastAction() {
        super("initiate_broadcast", "Records a message to broadcast across the colony for other citizens to hear. Only invoke this when the player explicitly requests a formal colony-wide announcement.",
                new ObjectProperty(new HashMap<>() {{
                    put("message", new PrimitiveProperty(PrimitiveProperty.Type.STRING, true));
                }}));
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && McTalkingConfig.INSTANCE.instance().enableBroadcastPropagation;
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var obj = new JsonObject();
        if (parameters == null || !parameters.has("message")) {
            obj.addProperty("success", false);
            obj.addProperty("error", "Missing or invalid parameters.");
            return obj;
        }

        String message = parameters.get("message").getAsString();
        String originatorName = citizen.getCitizenData() != null ? citizen.getCitizenData().getName() : "Unknown";
        String broadcastId = UUID.randomUUID().toString();

        ColonyBroadcast broadcast = new ColonyBroadcast(broadcastId, originatorName, message, System.currentTimeMillis());

        var memory = ((CitizenDataMemoryExtended) citizen.getCitizenData()).mc_talking$getOrInitializeMemory();
        memory.addBroadcast(broadcast);
        memory.addEvent("I sent a broadcast to the colony: " + message);

        obj.addProperty("success", true);
        obj.addProperty("broadcast_id", broadcastId);

        return obj;
    }
}
