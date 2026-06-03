package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class McTalkingDebugCommand {

    private McTalkingDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("talking_colonists")
                .requires(source -> source.hasPermission(2))
                .executes(McTalkingDebugCommand::overview);

        DebugStatusCommand.addTo(root);
        DebugConnectionsCommand.addTo(root);
        DebugCitizenCommand.addTo(root);
        DebugMemoryCommand.addTo(root);
        DebugEventsCommand.addTo(root);
        ListToolsCommand.addTo(root);

        dispatcher.register(root);
    }

    private static int overview(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var config = McTalkingConfig.INSTANCE.instance();
        boolean hasKey = !config.geminiApiKey.isEmpty();
        int activeSessions = ConversationManager.getClients().size();
        int playerSessions = ConversationManager.getCitizenToPlayer().size();
        int maxAgents = config.maxConcurrentAgents;

        source.sendSuccess(() -> Component.literal("")
                .append(Component.translatable("mc_talking.debug.header")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"))
                .append(Component.translatable(hasKey ? "mc_talking.debug.api_key_set" : "mc_talking.debug.api_key_empty")
                        .withStyle(hasKey ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal("\n"))
                .append(Component.translatable("mc_talking.debug.active_sessions",
                                activeSessions, maxAgents, playerSessions)
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.translatable("mc_talking.debug.config_toggles",
                                booleanToStr(config.enableCitizenMemory),
                                booleanToStr(config.enablePersonalityArchetypes),
                                booleanToStr(config.enableCitizenToCitizenConversation),
                                booleanToStr(config.enablePregeneration))
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("\n"))
                .append(Component.translatable("mc_talking.debug.cooldown",
                                config.citizenCooldownSeconds > 0
                                        ? String.valueOf(config.citizenCooldownSeconds)
                                        : "disabled")
                        .withStyle(ChatFormatting.GRAY)),
                false);
        return 1;
    }

    static String booleanToStr(boolean value) {
        return value ? "§a✔" : "§c✘";
    }

    static String formatDuration(long startTimeMs) {
        long elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
        if (elapsed < 60) return elapsed + "s";
        long mins = elapsed / 60;
        long secs = elapsed % 60;
        return mins + "m " + secs + "s";
    }

    static String cooldownRemaining(long lastEndTime, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return "none";
        long elapsed = (System.currentTimeMillis() - lastEndTime) / 1000;
        long remaining = cooldownSeconds - elapsed;
        if (remaining <= 0) return "ready";
        if (remaining < 60) return remaining + "s";
        long mins = remaining / 60;
        long secs = remaining % 60;
        return mins + "m " + secs + "s";
    }
}
