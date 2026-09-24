package me.sshcrack.mc_talking.handler;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.conversation.PlayerTextResult;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.internal.api.TalkingColonistsApiBackend;
import me.sshcrack.mc_talking.util.ChatToCitizenRouter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Typing to the citizen you are talking to (roadmap Q10). While a player is in a direct
 * conversation, chat lines starting with the configured prefix, or every line when the player
 * turned that on with {@code /citizen_chat}, go to the citizen through
 * {@code sendPlayerText} instead of server chat. The player sees their own line locally.
 */
public final class ChatToCitizenHandler {
    /** Kept under the loader's persisted player tag so it survives death and relogs. */
    private static final String ALL_CHAT_KEY = "mc_talking_chat_to_citizen";

    private ChatToCitizenHandler() {
    }

    /**
     * Handles one chat line.
     *
     * @return true when the line went to the citizen (or was refused) and must not reach server chat
     */
    public static boolean onChat(ServerPlayer player, String message) {
        var config = McTalkingConfig.INSTANCE.instance();
        if (!config.enableChatToCitizen) return false;
        AbstractEntityCitizen citizen = ConversationManager.getActiveEntityForPlayer(player.getUUID());
        if (citizen == null || ConversationManager.getActiveConversationKind(citizen.getUUID()) != ConversationKind.PLAYER) {
            return false;
        }
        String text = ChatToCitizenRouter.citizenText(message, config.chatToCitizenPrefix, isAllChatOn(player));
        if (text == null) return false;

        PlayerTextResult result = TalkingColonistsApiBackend.INSTANCE.conversations().sendPlayerText(player, citizen, text);
        switch (result.status()) {
            case DELIVERED -> player.sendSystemMessage(Component.translatable("mc_talking.chat_to_citizen.echo",
                    citizen.getName(), text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            // The conversation ended in between: let the line reach server chat as usual.
            case NOT_IN_CONVERSATION -> {
                return false;
            }
            default -> player.sendSystemMessage(Component.translatable(
                    "mc_talking.chat_to_citizen." + result.status().name().toLowerCase(Locale.ROOT),
                    PlayerTextResult.MAX_CHARS).withStyle(ChatFormatting.RED));
        }
        return true;
    }

    public static boolean isAllChatOn(ServerPlayer player) {
        return persisted(player).getBoolean(ALL_CHAT_KEY);
    }

    public static void setAllChat(ServerPlayer player, boolean on) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(ALL_CHAT_KEY, on);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
}
