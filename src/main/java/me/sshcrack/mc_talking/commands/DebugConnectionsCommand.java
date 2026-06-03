package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.duck.AbstractEntityCitizenAiStatusProvider;
import me.sshcrack.mc_talking.manager.CitizenWsClient;
import me.sshcrack.mc_talking.manager.GeminiWsClient;
import me.sshcrack.mc_talking.network.AiStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.UUID;

public class DebugConnectionsCommand {

    private DebugConnectionsCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("connections")
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        Map<UUID, GeminiWsClient> clients = ConversationManager.getClients();
        Map<UUID, UUID> citizenToPlayer = ConversationManager.getCitizenToPlayer();

        if (clients.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("mc_talking.debug.no_connections")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> {
            var msg = Component.literal("")
                    .append(Component.translatable("mc_talking.debug.connections_header", clients.size())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            for (var entry : clients.entrySet()) {
                UUID citizenId = entry.getKey();
                GeminiWsClient client = entry.getValue();
                AbstractEntityCitizen entity = client.getEntity();
                String citizenName = entity.getCitizenData() != null
                        ? entity.getCitizenData().getName()
                        : citizenId.toString().substring(0, 8) + "…";

                String playerName = null;
                UUID playerId = citizenToPlayer.get(citizenId);
                if (playerId != null) {
                    var player = source.getServer().getPlayerList().getPlayer(playerId);
                    if (player != null) playerName = player.getName().getString();
                }

                AiStatus status = ((AbstractEntityCitizenAiStatusProvider) entity).mc_talking$getAiStatus();
                String sessionType = (client instanceof CitizenWsClient cws && cws.isMumbling())
                        ? "mumble"
                        : "player";

                String duration = McTalkingDebugCommand.formatDuration(client.getSessionStartTimeMs());

                msg.append(Component.literal("\n  §7- "))
                        .append(Component.literal(citizenName).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" [").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(status.name()).withStyle(
                                status == AiStatus.TALKING ? ChatFormatting.GREEN :
                                        status == AiStatus.ERROR || status == AiStatus.QUOTA_EXCEEDED ? ChatFormatting.RED :
                                                status == AiStatus.THINKING ? ChatFormatting.YELLOW :
                                                        ChatFormatting.GRAY))
                        .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(sessionType).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(duration).withStyle(ChatFormatting.DARK_GRAY));

                if (playerName != null) {
                    msg.append(Component.literal(" §8(player: "))
                            .append(Component.literal(playerName).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            return msg;
        }, false);
        return 1;
    }
}
