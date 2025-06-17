package me.sshcrack.mc_talking.event;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.capability.EntityDataProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles entity-related events for data saving and loading
 */
@Mod.EventBusSubscriber(modid = McTalking.MODID)
public class EntityEventHandler {

    /**
     * When an entity joins a level, sync capabilities
     * @param event The entity join level event
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        
        // Nothing specific to do on join for now, but we could sync data here if needed
    }

    /**
     * When an entity dies, handle cleanup
     * @param event The living death event
     */
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        // Nothing specific to do on death for now
    }

    /**
     * When a player clones (respawns), copy capabilities
     * @param event The player clone event
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            Player original = event.getOriginal();
            Player newPlayer = event.getEntity();
            
            // Copy capability data from original to new player if needed
            original.getCapability(EntityDataProvider.ENTITY_DATA_CAPABILITY).ifPresent(oldData -> {
                newPlayer.getCapability(EntityDataProvider.ENTITY_DATA_CAPABILITY).ifPresent(newData -> {
                    newData.setSessionToken(oldData.getSessionToken());
                });
            });
        }
    }
}
