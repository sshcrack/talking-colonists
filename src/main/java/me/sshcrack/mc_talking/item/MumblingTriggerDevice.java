package me.sshcrack.mc_talking.item;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.manager.SystemControlledCitizenWsClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MumblingTriggerDevice extends Item {
    public MumblingTriggerDevice() {
        super(new Properties()
                .stacksTo(1)
                .setNoRepair());
    }

    @Override
    public boolean onLeftClickEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull Entity entity) {
        if (!(entity instanceof AbstractEntityCitizen citizen)) {
            return false; // Allow normal attack behavior for non-citizens
        }

        if (player.level().isClientSide()) {
            return true; // Prevent attack on client side
        }


        var client = new SystemControlledCitizenWsClient(citizen, c -> {
            c.addPromptTextAfterTalkingComplete("You continue mumbling.");
        });
        client.addPromptTextAfterTalkingComplete("You should start mumbling to yourself about your work and life.");
        player.sendSystemMessage(Component.literal("Started mumbling for selected citizen").withStyle(ChatFormatting.GREEN));

        return true; // Prevent normal attack behavior
    }
}
