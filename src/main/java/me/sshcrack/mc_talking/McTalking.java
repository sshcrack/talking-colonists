package me.sshcrack.mc_talking;

import com.mojang.logging.LogUtils;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.manager.AITools;
import me.sshcrack.mc_talking.network.AiStatusPayload;
import me.sshcrack.mc_talking.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
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
     * @param modEventBus  The mod event bus to register events
     * @param modContainer The mod container for configuration
     */
    public McTalking(IEventBus modEventBus, ModContainer modContainer) {
        // Register server events listener
        NeoForge.EVENT_BUS.register(new ServerEventHandler());

        // Register configuration
        modContainer.registerConfig(ModConfig.Type.COMMON, McTalkingConfig.SPEC);

        // Register other components
        ModItems.register(modEventBus);
        ModAttachmentTypes.register(modEventBus);
        modEventBus.addListener(this::registerPayloadHandlers);
        AITools.register();
    }

    /**
     * Registers network payload handlers for communication between client and server.
     *
     * @param event The payload handlers registration event
     */
    public void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playToClient(AiStatusPayload.TYPE, AiStatusPayload.STREAM_CODEC, new DirectionalPayloadHandler<>(
                (payload, ctx) -> {
                    ctx.enqueueWork(() -> {
                        ConversationManager.updateAiStatus(payload.citizen(), payload.status());
                    });
                },
                (a, b) -> {
                }
        ));
    }
}
