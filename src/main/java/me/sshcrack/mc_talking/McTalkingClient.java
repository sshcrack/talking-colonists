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
import net.minecraftforge.fml.common.Mod;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
/*?}*/
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mod class for McTalking.
 * Handles client-specific functionality like rendering and UI.
 */
/*? if forge {*/
/*@Mod.EventBusSubscriber(modid = McTalking.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
*//*?}*/
/*? if neoforge {*/
@Mod(value = McTalking.MODID, dist = Dist.CLIENT)
/*?}*/
public class McTalkingClient {
    // Track AI status for each entity
    private static final Map<UUID, AiStatus> aiStatus = new ConcurrentHashMap<>();


    /**
     * Updates the AI status for a specific entity
     *
     * @param entityId The UUID of the entity
     * @param status   The AI status to set
     */
    public static void updateAiStatus(UUID entityId, AiStatus status) {
        aiStatus.put(entityId, status);
    }


    /*? if neoforge {*/
    public McTalkingClient(ModContainer container) {
        NeoForge.EVENT_BUS.register(this);
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
    /*public static void onDisconnect(LevelEvent.Unload event) {
    *//*?}*/
    /*? if neoforge {*/
    public void onDisconnect(LevelEvent.Unload event) {
    /*?}*/
        aiStatus.clear();
    }

    /**
     * Event handler for rendering entity name tags.
     * Adds AI status indicators to citizen name tags.
     *
     * @param event The render name tag event
     */
    @SubscribeEvent
    /*? if forge {*/
    /*public static void onRenderName(RenderNameTagEvent event) {
    *//*?}*/
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

        var status = aiStatus.get(citizen.getUUID());
        if (status == null || status == AiStatus.NONE)
            return;

        var text = Component.literal(" (")
                .append(Component.translatable("mc_talking.ai_status." + status.name().toLowerCase()))
                .append(Component.literal(")"))
                .withStyle(ChatFormatting.GRAY);

        event.setContent(event.getContent().copy().append(text));
    }
}
