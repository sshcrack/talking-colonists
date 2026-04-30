package me.sshcrack.mc_talking;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.registry.ModItems;
/*? if forge {*/
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
/*?}*/
import org.slf4j.Logger;

/**
 * Main mod class for McTalking - a mod that enables citizens in MineColonies
 * to talk using AI voice chat.
 */
@Mod(McTalking.MODID)
public class McTalking {
    public static final String MODID = "mc_talking";
    public static final Logger LOGGER = LogUtils.getLogger();

    /*? if forge {*/
    /*public McTalking() {
        initialize();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McTalkingConfig.CONFIG_SPEC);
        ModItems.register(modEventBus);
    }
    *//*?}*/

    /*? if neoforge {*/
    public McTalking(IEventBus modEventBus, ModContainer modContainer) {
        initialize();

        NeoForge.EVENT_BUS.register(new ServerEventHandler());
        modContainer.registerConfig(ModConfig.Type.COMMON, McTalkingConfig.CONFIG_SPEC);
        ModItems.register(modEventBus);
    }
    /*?}*/

    private void initialize() {
        AITools.register();
    }
}
