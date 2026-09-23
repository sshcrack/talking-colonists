package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.config.AvailableAI;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaSnapshot;
import me.sshcrack.mc_talking.config.QuotaStatus;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.config.TtsQuotaManager;
import me.sshcrack.mc_talking.conversations.memory.MemoryCompactionService;
import me.sshcrack.mc_talking.pregen.PregenerationTaskService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

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
        boolean hasKey = McTalkingConfig.hasGeminiApiKey();

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

            for (AvailableAI model : AvailableAI.values()) {
                QuotaSnapshot snapshot = QuotaTracker.snapshot(model.getName());
                msg.append(Component.literal("  "))
                        .append(Component.translatable("mc_talking.debug.status_quota_model",
                                model.getName(), quotaStateStr(snapshot), resetEstimateStr(snapshot)))
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("\n"));
            }

            QuotaSnapshot ttsSnapshot = TtsQuotaManager.snapshot();
            msg.append(Component.literal("  "))
                    .append(Component.translatable("mc_talking.debug.status_quota_tts",
                            quotaStateStr(ttsSnapshot), resetEstimateStr(ttsSnapshot)))
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

    private static String quotaStateStr(QuotaSnapshot snapshot) {
        return snapshot.status() == QuotaStatus.EXHAUSTED ? "§cexceeded" : "§aok";
    }

    private static String resetEstimateStr(QuotaSnapshot snapshot) {
        if (snapshot.status() != QuotaStatus.EXHAUSTED || snapshot.resetAtMs() == null) {
            return "unknown";
        }
        Duration remaining = Duration.between(Instant.now(), Instant.ofEpochMilli(snapshot.resetAtMs()));
        if (remaining.isNegative()) return "unknown";
        long totalSeconds = remaining.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String wallClock = DateTimeFormatter.ofPattern("HH:mm:ss")
                .format(Instant.ofEpochMilli(snapshot.resetAtMs()).atZone(java.time.ZoneId.systemDefault()));
        return String.format("~%dm%ds (around %s)", minutes, seconds, wallClock);
    }
}
