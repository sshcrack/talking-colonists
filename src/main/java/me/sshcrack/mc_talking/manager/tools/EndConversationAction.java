package me.sshcrack.mc_talking.manager.tools;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EndConversationAction extends GeneralFunctionAction {
    public EndConversationAction() {
        super("end_conversation", """
                This will end the conversation. The peer you are talking to isn't able to respond after. Only use if you are sure you want to end the conversation.
                """);
    }

    @NotNull
    @Override
    public JsonObject execute(AbstractEntityCitizen citizen, IColony colony, @Nullable JsonObject parameters) {
        var client = ConversationManager.getClientForEntity(citizen.getUUID());
        var obj = new JsonObject();

        if (client == null) {
            obj.addProperty("success", false);
            obj.addProperty("error", "No active conversation found.");
            return obj;
        }

        client.endConversationWhenPossible();
        obj.addProperty("success", true);

        var playerUUID = ConversationManager.getPlayerForEntity(citizen.getUUID());
        if (playerUUID != null) {
            var server = citizen.level().getServer();
            if (server != null) {
                var player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    player.sendSystemMessage(
                            Component.translatable("mc_talking.colonist_ended_conversation")
                                    .withStyle(ChatFormatting.YELLOW));
                }
            }
        }

        return obj;
    }
}
