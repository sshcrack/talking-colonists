package me.sshcrack.mc_talking;

import com.minecolonies.api.entity.citizen.AbstractCivilianEntity;
import me.sshcrack.mc_talking.network.AiStatus;
import me.sshcrack.mc_talking.client.ConversationPresentation;
/*? if forge {*/
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
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
        if (status == AiStatus.NONE) {
            aiStatus.remove(entityId);
        } else {
            aiStatus.put(entityId, status);
        }
    }


    /*? if neoforge {*/
    public McTalkingClient(ModContainer container) {
        NeoForge.EVENT_BUS.register(this);
        /*? if devtools {*/
        /*me.sshcrack.mc_talking.devtools.DevAutoQuit.init();
        *//*?}*/
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
        if (event.getLevel().isClientSide()) aiStatus.clear();
    }

    public static AiStatus getAiStatus(UUID entityId) {
        return aiStatus.getOrDefault(entityId, AiStatus.NONE);
    }

    @SubscribeEvent
    /*? if forge {*/
    /*public static void onEntityLeave(EntityLeaveLevelEvent event) {
    *//*?}*/
    /*? if neoforge {*/
    public void onEntityLeave(EntityLeaveLevelEvent event) {
    /*?}*/
        if (event.getLevel().isClientSide()) aiStatus.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    /*? if forge {*/
    /*public static void onRenderCitizen(RenderLivingEvent.Post<?, ?> event) {
    *//*?}*/
    /*? if neoforge {*/
    public void onRenderCitizen(RenderLivingEvent.Post<?, ?> event) {
    /*?}*/
        if (event.getEntity() instanceof AbstractCivilianEntity citizen) {
            ConversationPresentation.renderBubble(citizen, event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    /*? if forge {*/
    /*public static void onRenderGui(RenderGuiEvent.Post event) {
    *//*?}*/
    /*? if neoforge {*/
    public void onRenderGui(RenderGuiEvent.Post event) {
    /*?}*/
        ConversationPresentation.renderFocusHint(event.getGuiGraphics());
    }
}
