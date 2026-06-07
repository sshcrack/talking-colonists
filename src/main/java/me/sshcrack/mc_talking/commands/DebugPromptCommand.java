package me.sshcrack.mc_talking.commands;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import me.sshcrack.mc_talking.ConversationManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;

public class DebugPromptCommand {

    private static final SimpleCommandExceptionType NOT_A_CITIZEN =
            new SimpleCommandExceptionType(Component.translatable("mc_talking.debug.not_citizen"));

    private DebugPromptCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("prompt")
                .then(Commands.argument("citizen", EntityArgument.entity())
                        .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                .executes(ctx -> execute(ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "citizen"),
                                        StringArgumentType.getString(ctx, "prompt"))))));
    }

    private static int execute(CommandSourceStack source, net.minecraft.world.entity.Entity target, String prompt) throws CommandSyntaxException {
        if (!(target instanceof AbstractEntityCitizen citizen)) {
            throw NOT_A_CITIZEN.create();
        }

        ConversationManager.forceRemoveCooldown(citizen);
        ConversationManager.startLowPrioritySession(citizen, prompt);

        String citizenName = citizen.getCitizenData() != null
                ? citizen.getCitizenData().getName()
                : citizen.getUUID().toString().substring(0, 8);

        source.sendSuccess(() -> Component.translatable("mc_talking.debug.prompt_triggered", citizenName, prompt)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
