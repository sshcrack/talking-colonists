package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side mod class for McTalking.
 * Handles client-specific functionality like rendering and UI.
 */
@Mod.EventBusSubscriber(modid = McTalking.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class McTalkingClient {

    /**
     * Event handler for when the client disconnects from a server.
     * Clears all AI status data.
     *
     * @param event The level unload event
     */
    @SubscribeEvent
    public static void onDisconnect(LevelEvent.Unload event) {
        ConversationManager.clearAiStatus();
    }

    /**
     * Event handler for rendering entity name tags.
     * Adds AI status indicators to citizen name tags.
     *
     * @param event The render name tag event
     */
    @SubscribeEvent
    public static void onRenderName(RenderNameTagEvent event) {
        var entity = event.getEntity();
        var minecraft = Minecraft.getInstance();

        // Only handle citizens
        if (!(entity instanceof AbstractCivilianEntity citizen)) {
            return;
        }

        // Skip if player is null or entity is invisible
        assert minecraft.player != null;
        if (entity.isInvisibleTo(minecraft.player))
            return;

        var status = ConversationManager.getAiStatus(citizen.getUUID());
        if (status == AiStatus.NONE)
            return;

        var text = Component.literal(" (")
                .append(Component.translatable("mc_talking.ai_status." + status.name().toLowerCase()))
                .append(Component.literal(")"))
                .withStyle(ChatFormatting.GRAY);

        event.setContent(event.getContent().copy().append(text));
    }
}
