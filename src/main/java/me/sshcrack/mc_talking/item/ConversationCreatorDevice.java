package me.sshcrack.mc_talking.item;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.conversations.CitizenConversation;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConversationCreatorDevice extends Item {
    public ConversationCreatorDevice() {
        super(new Properties()
                .stacksTo(1)
                .setNoRepair());
    }

    private final Set<AbstractEntityCitizen> conversationParticipants = new HashSet<>();

    @Override
    public boolean onLeftClickEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull Entity entity) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return false; // Allow normal attack behavior for non-citizens
        }

        if (player.level().isClientSide()) {
            return true; // Prevent attack on client side
        }

        conversationParticipants.add(citizen);
        player.sendSystemMessage(Component.literal("Added " + citizen.getName().getString() + " to the conversation").withStyle(ChatFormatting.GREEN));

        return true; // Prevent normal attack behavior
    }

    @Override
    public boolean onDroppedByPlayer(@NotNull ItemStack item, @NotNull Player player) {
        conversationParticipants.clear();

        return true;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        if (level.isClientSide()) {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }

        ServerLevel serverLevel = (ServerLevel) level;

        List<AbstractEntityCitizen> participants = new ArrayList<>(conversationParticipants);
        var conversation = new CitizenConversation(serverLevel.getServer(), participants);


        player.sendSystemMessage(Component.literal("Started conversation with " + participants.size() + " participants").withStyle(ChatFormatting.GREEN));
        conversation.setOnStateChanged(newState -> {
            AiStatus newStatus = switch (newState) {
                case GENERATING -> AiStatus.THINKING;
                case PLAYING_AUDIO -> AiStatus.IN_CONVERSATION;
                case ENDED -> AiStatus.NONE;
            };

            for (AbstractEntityCitizen participant : participants) {
                ConversationManager.updateAiStatus(participant.getUUID(), newStatus);
            }
        });
        return InteractionResultHolder.pass(player.getItemInHand(usedHand));
    }
}
