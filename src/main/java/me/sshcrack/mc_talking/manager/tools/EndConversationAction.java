package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EndConversationAction extends GeneralFunctionAction {
    public EndConversationAction() {
        super("end_conversation", """
                Terminates the current autonomous conversation. The other party will not be able to respond.
                WARNING: This CANNOT be used while a real player is speaking to you directly.
                Only use this in autonomous contexts (mumbling, citizen-to-citizen conversations).
                """);
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var playerUUID = ConversationManager.getPlayerForEntity(citizen.getUUID());
        if (playerUUID != null) {
            var obj = new JsonObject();
            obj.addProperty("success", false);
            obj.addProperty("error", "You cannot end this conversation — a player is speaking to you. Continue talking to them.");
            return obj;
        }

        var client = ConversationManager.getClientForEntity(citizen.getUUID());
        var obj = new JsonObject();

        if (client == null) {
            obj.addProperty("success", false);
            obj.addProperty("error", "No active conversation found.");
            return obj;
        }

        client.endConversationWhenPossible();
        obj.addProperty("success", true);

        return obj;
    }
}
