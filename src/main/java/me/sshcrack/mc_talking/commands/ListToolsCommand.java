package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ListToolsCommand {
    private static int run(int page, CommandContext<CommandSourceStack> context) {
        var src = context.getSource();
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("list_tools")
                        .executes(context -> {
                            return run(0, context);
                        })
                        .then(RequiredArgumentBuilder.argument("page", IntegerArgumentType.integer(0)))
                        .executes(context -> {
                            var page = IntegerArgumentType.getInteger(context, "page");
                            return run(page, context);
                        })
        );
    }
}
