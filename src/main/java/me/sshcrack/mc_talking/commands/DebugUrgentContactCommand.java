package me.sshcrack.mc_talking.commands;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.sshcrack.mc_talking.ServerEventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class DebugUrgentContactCommand {

    private static final SimpleCommandExceptionType NOT_A_CITIZEN =
            new SimpleCommandExceptionType(Component.translatable("mc_talking.debug.not_citizen"));

    private static final SimpleCommandExceptionType NOT_A_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("mc_talking.debug.not_player"));

    private DebugUrgentContactCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("urgent_contact")
                .then(Commands.argument("citizen", EntityArgument.entity())
                        .executes(ctx -> execute(ctx.getSource(), EntityArgument.getEntity(ctx, "citizen")))));
    }

    private static int execute(CommandSourceStack source, net.minecraft.world.entity.Entity target) throws CommandSyntaxException {
        if (!(target instanceof AbstractEntityCitizen citizen)) {
            throw NOT_A_CITIZEN.create();
        }

        var sender = source.getEntity();
        if (!(sender instanceof ServerPlayer player)) {
            throw NOT_A_PLAYER.create();
        }

        ServerEventHandler.triggerWalkToPlayer(citizen, player);

        String citizenName = citizen.getCitizenData() != null
                ? citizen.getCitizenData().getName()
                : citizen.getUUID().toString().substring(0, 8);

        source.sendSuccess(() -> Component.translatable("mc_talking.debug.urgent_contact_triggered", citizenName)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
