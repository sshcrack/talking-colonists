package me.sshcrack.mc_talking.commands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.broadcast.ColonyBroadcast;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenMemories;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipMemory;
import me.sshcrack.mc_talking.duck.CitizenDataMemoryExtended;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public class DebugMemoryCommand {
    private static final SimpleCommandExceptionType NOT_A_CITIZEN =
            new SimpleCommandExceptionType(Component.translatable("mc_talking.debug.not_citizen"));

    private DebugMemoryCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("memory")
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))));
    }

    private static int execute(CommandSourceStack source, net.minecraft.world.entity.Entity target) throws CommandSyntaxException {
        if (!(target instanceof AbstractEntityCitizen citizen)) {
            throw NOT_A_CITIZEN.create();
        }

        ICitizenData data = citizen.getCitizenData();
        if (data == null) {
            source.sendFailure(Component.translatable("mc_talking.debug.no_citizen_data"));
            return 0;
        }

        CitizenMemories mem = ((CitizenDataMemoryExtended) data).mc_talking$getMemory();
        if (mem == null) {
            source.sendSuccess(() -> Component.translatable("mc_talking.debug.memory_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        String sessionToken = mem.getSessionToken();
        boolean hasSessionToken = sessionToken != null && !sessionToken.isBlank();
        var facts = mem.getFacts();
        var events = mem.getEvents();
        var relationships = mem.getRelationships();

        source.sendSuccess(() -> {
            var msg = Component.literal("")
                    .append(Component.translatable("mc_talking.debug.memory_header", data.getName())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(Component.literal("  §7Session Token: §f")
                            .append(Component.literal(hasSessionToken ? "§a§lSET" : "§8none"))
                            .append(Component.literal("\n")));

            // Summarized Memory (compaction)
            String summarized = mem.getSummarizedMemory();
            msg.append(Component.literal("  §7Summarized Memory (" + summarized.length() + " chars):\n"));
            if (summarized.isBlank()) {
                msg.append(Component.literal("    §8(none)\n"));
            } else {
                msg.append(Component.literal("    §f" + summarized + "\n"));
            }

            // Facts
            msg.append(Component.literal("  §7Facts (" + facts.size() + "):\n"));
            if (facts.isEmpty()) {
                msg.append(Component.literal("    §8(none)\n"));
            } else {
                int i = 1;
                for (String fact : facts) {
                    msg.append(Component.literal("    §f" + i + ". ")
                            .append(Component.literal(fact).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("\n")));
                    i++;
                }
            }

            // Events
            msg.append(Component.literal("  §7Events (" + events.size() + "):\n"));
            if (events.isEmpty()) {
                msg.append(Component.literal("    §8(none)\n"));
            } else {
                int i = 1;
                for (String event : events) {
                    msg.append(Component.literal("    §f" + i + ". ")
                            .append(Component.literal(event).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("\n")));
                    i++;
                }
            }

            // Relationships
            msg.append(Component.literal("  §7Relationships (" + relationships.size() + "):\n"));
            if (relationships.isEmpty()) {
                msg.append(Component.literal("    §8(none)\n"));
            } else {
                for (int i = 0; i < relationships.size(); i++) {
                    CitizenRelationshipMemory rel = relationships.get(i);
                    String targetStr = rel.getTargetUUID().toString().substring(0, 8) + "…";
                    msg.append(Component.literal("    §f" + (i + 1) + ". §7" + targetStr
                                    + " §8| §f" + rel.getType().name()
                                    + " §8| §ffactor=" + String.format("%.2f", rel.getFactor()))
                            .append(Component.literal("\n")));
                }
            }

            // Broadcasts
            var broadcasts = mem.getReceivedBroadcasts();
            msg.append(Component.literal("  §7Broadcasts (" + broadcasts.size() + "):\n"));
            if (broadcasts.isEmpty()) {
                msg.append(Component.literal("    §8(none)\n"));
            } else {
                int i = 1;
                for (ColonyBroadcast b : broadcasts) {
                    long ageMs = System.currentTimeMillis() - b.getCreatedAtMs();
                    String ageStr = formatAge(ageMs);
                    String idStr = b.getId().length() > 8 ? b.getId().substring(0, 8) + "…" : b.getId();
                    msg.append(Component.literal("    §f" + i + ". §7" + idStr
                                    + " §8| §f" + b.getOriginatorName()
                                    + " §8| §7\"" + b.getMessage() + "\""
                                    + " §8(" + ageStr + ")")
                            .append(Component.literal("\n")));
                    i++;
                }
            }

            // Pending Rumors
            int pendingRumors = mem.getPendingRumorCount();
            msg.append(Component.literal("  §7Pending Rumors (" + pendingRumors + "):\n"));
            if (pendingRumors == 0) {
                msg.append(Component.literal("    §8(none)"));
            } else {
                for (int i = 0; i < pendingRumors; i++) {
                    String rumor = mem.peekPendingRumor(i);
                    msg.append(Component.literal("    §f" + (i + 1) + ". ")
                            .append(Component.literal(rumor).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal("\n")));
                }
            }

            return msg;
        }, false);
        return 1;
    }

    private static String formatAge(long ageMs) {
        long seconds = ageMs / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes < 60) return minutes + "m" + seconds + "s";
        long hours = minutes / 60;
        minutes %= 60;
        return hours + "h" + minutes + "m";
    }
}
