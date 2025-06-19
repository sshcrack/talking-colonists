package me.sshcrack.mc_talking;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.capability.CapabilityHandler;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import me.sshcrack.mc_talking.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Main mod class for McTalking - a mod that enables citizens in MineColonies
 * to talk using AI voice chat.
 */
@Mod(McTalking.MODID)
public class McTalking {
    public static final String MODID = "mc_talking";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static boolean isDedicated = false;

    /**
     * Constructor for the mod class.
     * Registers event listeners, configurations, and initializes necessary components.
     *
     */
    public McTalking() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register server events listener
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());

        // Register configuration
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McTalkingConfig.CONFIG_SPEC);

        // Register other components
        ModItems.register(modEventBus);
        modEventBus.addListener(this::registerCapabilities);
        
        // Initialize network
        AiStatusPayload.registerMessages();
        
        AITools.register();
    }/**
     * Registers capabilities for the mod
     *
     * @param event The capabilities registration event
     */
    public void registerCapabilities(final RegisterCapabilitiesEvent event) {
        CapabilityHandler.register(event);
    }
}
