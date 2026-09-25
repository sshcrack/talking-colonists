package me.sshcrack.mc_talking.item;

import me.sshcrack.mc_talking.client.HandbookWindow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Colony Handbook: opens a window with one chapter per feature (Talking Colonists and its addons). */
public class ColonyHandbookItem extends Item {
    public ColonyHandbookItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // The guides are registered on the client too, so the window needs nothing from the server.
        if (level.isClientSide()) HandbookWindow.openHandbook();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
