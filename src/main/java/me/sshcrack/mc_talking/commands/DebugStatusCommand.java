package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class DebugStatusCommand {

    private DebugStatusCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("status")
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        var config = McTalkingConfig.INSTANCE.instance();
        var clients = ConversationManager.getClients();
        var citizenToPlayer = ConversationManager.getCitizenToPlayer();

        int activeSessions = clients.size();
        int playerSessions = citizenToPlayer.size();
        int nonPlayerSessions = activeSessions - playerSessions;
        int maxAgents = config.maxConcurrentAgents;
        boolean hasKey = !config.geminiApiKey.isEmpty();

        source.sendSuccess(() -> {
            var msg = Component.literal("")
                    .append(Component.translatable("mc_talking.debug.status_header")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n"));

            // API key
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.api_key_status",
                            McTalkingDebugCommand.booleanToStr(hasKey)))
                    .withStyle(hasKey ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.literal("\n"));

            // Sessions
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_sessions",
                            activeSessions, maxAgents, playerSessions, nonPlayerSessions))
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("\n"));

            // Cooldown
            String cooldownStr = config.citizenCooldownSeconds > 0
                    ? config.citizenCooldownSeconds + "s"
                    : "disabled";
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_cooldown", cooldownStr))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));

            // Toggles
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_memory",
                            config.enableConversationSummaryAndMemorize ? "Compaction after conversation" : "Live during Conversation"))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_personality",
                            McTalkingDebugCommand.booleanToStr(config.enablePersonalityArchetypes)))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_citizen_to_citizen",
                            McTalkingDebugCommand.booleanToStr(config.enableCitizenToCitizenConversation)))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_pregeneration",
                            McTalkingDebugCommand.booleanToStr(config.enablePregeneration)))
                    .withStyle(ChatFormatting.GRAY);

            return msg;
        }, false);
        return 1;
    }
}
