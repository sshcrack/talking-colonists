package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
/*? if forge {*/
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;*/
/*?}*/
/*? if neoforge {*/
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
/*?}*/

/**
 * Client-side mod class for McTalking.
 * Handles client-specific functionality like rendering and UI.
 */
/*? if forge {*/
/*@Mod.EventBusSubscriber(modid = McTalking.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)*/
/*?}*/
/*? if neoforge {*/
@Mod(value = McTalking.MODID, dist = Dist.CLIENT)
/*?}*/
public class McTalkingClient {

    /*? if neoforge {*/
    public McTalkingClient(ModContainer container) {
        NeoForge.EVENT_BUS.register(this);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
    /*?}*/

    /**
     * Event handler for when the client disconnects from a server.
     * Clears all AI status data.
     *
     * @param event The level unload event
     */
    @SubscribeEvent
    /*? if forge {*/
    /*public static void onDisconnect(LevelEvent.Unload event) {*/
    /*?}*/
    /*? if neoforge {*/
    public void onDisconnect(LevelEvent.Unload event) {
    /*?}*/
        ConversationManager.clearAiStatus();
    }

    /**
     * Event handler for rendering entity name tags.
     * Adds AI status indicators to citizen name tags.
     *
     * @param event The render name tag event
     */
    @SubscribeEvent
    /*? if forge {*/
    /*public static void onRenderName(RenderNameTagEvent event) {*/
    /*?}*/
    /*? if neoforge {*/
    public void onRenderName(RenderNameTagEvent event) {
    /*?}*/
        var entity = event.getEntity();
        var minecraft = Minecraft.getInstance();

        if (!(entity instanceof AbstractCivilianEntity citizen)) {
            return;
        }

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
