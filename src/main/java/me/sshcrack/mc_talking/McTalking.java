package me.sshcrack.mc_talking;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.listener.ColonyEventSubscriber;
import me.sshcrack.mc_talking.manager.tools.AITools;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import me.sshcrack.mc_talking.registry.ModItems;
/*? if forge {*/
/*import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
*//*?}*/
/*? if neoforge {*/
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
/*?}*/
import org.slf4j.Logger;

/**
 * Main mod class for McTalking - a mod that enables citizens in MineColonies
 * to talk using AI voice chat.
 */
@Mod(McTalking.MODID)
public class McTalking {
    @SuppressWarnings("SpellCheckingInspection")
    public static final String MODID = "mc_talking";
    public static final Logger LOGGER = LogUtils.getLogger();

    /*? if forge {*/
    /*public McTalking() {
        initialize();

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((client, parent) -> McTalkingConfig.INSTANCE.generateGui().generateScreen(parent))
        );

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
        ModItems.register(modEventBus);
        modEventBus.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, this::onCommonSetup);
    }
    *//*?}*/

    /*? if neoforge {*/
    public McTalking(IEventBus modEventBus, ModContainer modContainer) {
        initialize();

        ModLoadingContext.get().registerExtensionPoint(
                IConfigScreenFactory.class,
                () -> (client, parent) -> McTalkingConfig.INSTANCE.generateGui().generateScreen(parent)
        );

        NeoForge.EVENT_BUS.register(new ServerEventHandler());
        ModItems.register(modEventBus);
        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(FMLCommonSetupEvent.class, this::onCommonSetup);
    }
    /*?}*/

    private void initialize() {
        AITools.register();
        McTalkingConfig.loadConfig();
        AiStatusPayload.registerMessages();
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        ColonyEventSubscriber.register();
    }


    /*? if neoforge {*/
    public void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playToClient(AiStatusPayload.TYPE, AiStatusPayload.STREAM_CODEC, new DirectionalPayloadHandler<>(
                (payload, ctx) -> ctx.enqueueWork(() -> McTalkingClient.updateAiStatus(payload.citizen(), payload.status())),
                (a, b) -> {
                }
        ));
    }
    /*?}*/
}
