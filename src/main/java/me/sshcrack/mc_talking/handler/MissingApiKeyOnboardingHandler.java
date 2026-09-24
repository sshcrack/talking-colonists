package me.sshcrack.mc_talking.handler;

import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.onboarding.MissingKeyOnboardingTracker;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import me.sshcrack.mc_talking.client.ConfigScreenOpener;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Notifies operators (and the single-player/LAN host) that no Gemini API key is
 * configured, once per player for the lifetime of the running server.
 */
public final class MissingApiKeyOnboardingHandler {
    private MissingApiKeyOnboardingHandler() {
    }

    /**
     * Name of the client-side command (registered in {@code McTalkingClient}) that
     * opens the Talking Colonists config screen. Kept here so the server-built chat
     * message and the client-registered command cannot drift apart.
     */
    public static final String OPEN_CONFIG_CLIENT_COMMAND = "mc_talking_open_config";

    private static final String API_KEY_URL = "https://aistudio.google.com/apikey";

    private static final MissingKeyOnboardingTracker tracker = new MissingKeyOnboardingTracker();

    public static void onPlayerLoggedIn(ServerPlayer player) {
        if (McTalkingConfig.hasGeminiApiKey()) {
            return;
        }

        MinecraftServer server = player.getServer();
        boolean isOperator = player.hasPermissions(Commands.LEVEL_GAMEMASTERS)
                || (server != null && server.isSingleplayerOwner(player.getGameProfile()));

        if (!tracker.shouldNotify(player.getUUID(), isOperator)) {
            return;
        }

        MutableComponent link = Component.translatable("mc_talking.onboarding.get_key_link")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, API_KEY_URL))
                        .withUnderlined(true)
                        .withColor(ChatFormatting.AQUA));

        MutableComponent message = Component.translatable("mc_talking.onboarding.missing_key")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(link);

        if (server != null && !server.isDedicatedServer()) {
            MutableComponent openConfig = Component.translatable("mc_talking.onboarding.open_config_link")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + OPEN_CONFIG_CLIENT_COMMAND))
                            .withUnderlined(true)
                            .withColor(ChatFormatting.YELLOW));
            message = message.append(Component.literal(" ")).append(openConfig);
        }

        player.sendSystemMessage(message);
    }

    /**
     * Server-side twin of the client command, for Forge 1.20.1, which sends clicked chat commands
     * straight to the server instead of trying client commands first. The link is only offered on
     * integrated servers, which share the JVM with the host's client, so the command opens the
     * screen there directly. Registered only for integrated servers and only usable by the host.
     */
    public static void registerServerFallback(CommandDispatcher<CommandSourceStack> dispatcher,
                                              Commands.CommandSelection selection) {
        if (selection == Commands.CommandSelection.DEDICATED) return;
        dispatcher.register(Commands.literal(OPEN_CONFIG_CLIENT_COMMAND)
                .requires(source -> source.getServer() != null && !source.getServer().isDedicatedServer()
                        && source.getPlayer() != null
                        && source.getServer().isSingleplayerOwner(source.getPlayer().getGameProfile()))
                .executes(ctx -> {
                    ConfigScreenOpener.open();
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** Resets the once-per-session notification state. Call when the server starts. */
    public static void onServerStart() {
        tracker.reset();
    }
}
