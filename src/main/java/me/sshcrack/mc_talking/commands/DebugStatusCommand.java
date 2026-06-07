package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.conversations.memory.MemoryCompactionService;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
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
        int pregenActive = PregenerationTaskService.isPregenerating() ? 1 : 0;
        int compactionActive = MemoryCompactionService.getActiveCount();
        int totalActive = activeSessions + pregenActive + compactionActive;
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
                            totalActive, maxAgents, playerSessions, nonPlayerSessions, pregenActive, compactionActive))
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
                            McTalkingDebugCommand.booleanToStr(config.enablePregeneration),
                            PregenerationTaskService.isPregenerating() ? "§aactive" : "§7idle"))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_compaction",
                            McTalkingDebugCommand.booleanToStr(config.enableMemoryCompaction),
                            config.memoryMode.name(),
                            MemoryCompactionService.getActiveCount()))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));

            boolean liveQuotaExceeded = QuotaTracker.isQuotaExceeded(McTalkingConfig.CHEAP_LIVE_MODEL.getName());
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_quota",
                            liveQuotaExceeded ? "§cexceeded" : "§aok"))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("\n"));

            int usedBg = ConversationManager.getUsedBackgroundSlots();
            int maxBg = ConversationManager.getMaxBackgroundSlots();
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_bg_slots", usedBg, maxBg))
                    .withStyle(ChatFormatting.GRAY);

            return msg;
        }, false);
        return 1;
    }
}
