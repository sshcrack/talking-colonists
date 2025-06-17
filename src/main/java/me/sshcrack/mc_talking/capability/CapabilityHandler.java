package me.sshcrack.mc_talking.capability;

import me.sshcrack.mc_talking.McTalking;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles registration and attachment of capabilities
 */
@Mod.EventBusSubscriber(modid = McTalking.MODID)
public class CapabilityHandler {

    /**
     * Register capabilities with Forge
     * @param event The register capabilities event
     */
    public static void register(RegisterCapabilitiesEvent event) {
        event.register(EntityDataProvider.class);
    }

    /**
     * Attach capabilities to entities
     * @param event The attach capabilities event
     */
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Entity) {
            EntityDataProvider provider = new EntityDataProvider();
            event.addCapability(EntityDataProvider.IDENTIFIER, provider);
        }
    }
}
