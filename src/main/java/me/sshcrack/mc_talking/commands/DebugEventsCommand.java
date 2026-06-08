package me.sshcrack.mc_talking.commands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.sshcrack.mc_talking.util.ColonyEventBuffer;
import net.minecraft.ChatFormatting;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class DebugEventsCommand {

    private DebugEventsCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("events")
                .executes(ctx -> execute(ctx.getSource(), -1))
                .then(Commands.argument("colony_id", IntegerArgumentType.integer())
                        .executes(ctx -> execute(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "colony_id")))));
    }

    private static int execute(CommandSourceStack source, int colonyId) {
        if (colonyId < 0) {
            ServerPlayer player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.translatable("mc_talking.debug.no_colony_specified"));
                return 0;
            }
            IColony colony = IColonyManager.getInstance().getIColonyByOwner(player.serverLevel(), player.getUUID());
            if (colony == null) {
                colony = IColonyManager.getInstance().getColonyByPosFromWorld(player.level(), player.blockPosition());
            }
            if (colony == null) {
                source.sendFailure(Component.translatable("mc_talking.debug.no_colony_found"));
                return 0;
            }
            colonyId = colony.getID();
        }

        IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, source.getLevel());
        if (colony == null) {
            source.sendFailure(Component.translatable("mc_talking.debug.no_colony_found"));
            return 0;
        }
        String colonyName = colony.getName();
        int eventWindow = me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().colonyEventWindowSeconds;
        List<ColonyEventBuffer.ColonyEvent> recentEvents = ColonyEventBuffer.getRecentEvents(colony, eventWindow > 0 ? eventWindow : 300);

        long millisSinceRaid = ColonyEventBuffer.millisSinceRaid(colony);
        int lostCitizens = ColonyEventBuffer.getLostCitizens(colony);
        final int fColonyId = colonyId;

        source.sendSuccess(() -> {
            var msg = Component.literal("")
                    .append(Component.translatable("mc_talking.debug.events_header", colonyName, fColonyId)
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n"));

            // Raid info
            if (millisSinceRaid < Long.MAX_VALUE) {
                long secondsSinceRaid = millisSinceRaid / 1000;
                String raidStr;
                if (secondsSinceRaid < 60) raidStr = secondsSinceRaid + "s ago";
                else if (secondsSinceRaid < 3600) raidStr = (secondsSinceRaid / 60) + "m " + (secondsSinceRaid % 60) + "s ago";
                else raidStr = (secondsSinceRaid / 3600) + "h ago";
                msg.append(Component.literal("  §7Last raid: §f")
                        .append(Component.literal(raidStr).withStyle(ChatFormatting.RED))
                        .append(Component.literal(" §7(" + lostCitizens + " citizens lost)\n")));
            } else {
                msg.append(Component.literal("  §7Last raid: §8none\n"));
            }

            if (me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().raidTraumaDurationSeconds > 0) {
                boolean inTrauma = ColonyEventBuffer.isInTrauma(colony,
                        me.sshcrack.mc_talking.config.McTalkingConfig.INSTANCE.instance().raidTraumaDurationSeconds);
                msg.append(Component.literal("  §7Raid trauma: ")
                        .append(Component.literal(inTrauma ? "§cACTIVE" : "§ainactive"))
                        .append(Component.literal("\n")));
            }

            // Recent events
            msg.append(Component.literal("  §7Recent events (" + recentEvents.size() + "):\n"));
            if (recentEvents.isEmpty()) {
                msg.append(Component.literal("    §8(none)"));
            } else {
                for (int i = 0; i < recentEvents.size(); i++) {
                    ColonyEventBuffer.ColonyEvent evt = recentEvents.get(i);
                    long ageSec = (System.currentTimeMillis() - evt.timestampMs()) / 1000;
                    String ageStr = ageSec < 60 ? ageSec + "s" : (ageSec / 60) + "m " + (ageSec % 60) + "s";
                    msg.append(Component.literal("    §f" + (i + 1) + ". §7[" + evt.type().name() + "] ")
                            .append(Component.literal(evt.description()).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" §8(" + ageStr + " ago)"))
                            .append(Component.literal("\n")));
                }
            }

            return msg;
        }, false);
        return 1;
    }
}
