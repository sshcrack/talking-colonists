package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.conversations.LiveConversationWsClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EndConversationAction extends FunctionAction {
    public EndConversationAction() {
        super("end_conversation", """
                Ends the conversation between you and another fellow citizen. Not usable if talking to a player. Use this when you want to stop the current conversation
                """);
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var client = ConversationManager.getClientForEntity(citizen.getUUID());
        var obj = new JsonObject();

        if (!(client instanceof LiveConversationWsClient liveClient) || liveClient.getPeer() == null) {
            obj.addProperty("success", false);
            obj.addProperty("error", "You are in a conversation with a player. Can not end conversation.");
            return obj;
        }

        liveClient.forceConversationEnd();
        obj.addProperty("success", true);

        return obj;
    }
}
