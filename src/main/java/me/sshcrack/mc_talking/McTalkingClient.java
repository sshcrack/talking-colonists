package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Client-side mod class for McTalking.
 * Handles client-specific functionality like rendering and UI.
 */
@Mod(value = McTalking.MODID, dist = Dist.CLIENT)
public class McTalkingClient {

    /**
     * Constructor for the client mod class.
     * Registers event listeners and configuration screen.
     *
     * @param container The mod container
     */
    public McTalkingClient(ModContainer container) {
        // Register event listeners
        NeoForge.EVENT_BUS.register(this);

        // Register configuration screen
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * Event handler for when the client disconnects from a server.
     * Clears all AI status data.
     *
     * @param event The level unload event
     */
    @SubscribeEvent
    public void onDisconnect(LevelEvent.Unload event) {
        ConversationManager.clearAiStatus();
    }

    /**
     * Event handler for rendering entity name tags.
     * Adds AI status indicators to citizen name tags.
     *
     * @param event The render name tag event
     */
    @SubscribeEvent
    public void onRenderName(RenderNameTagEvent event) {
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
