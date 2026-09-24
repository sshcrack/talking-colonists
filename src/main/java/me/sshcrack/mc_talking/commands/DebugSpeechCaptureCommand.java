package me.sshcrack.mc_talking.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.sshcrack.mc_talking.api.speech.PlayerSpeechCapture;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;

/**
 * {@code /talking_colonists speech_capture [seconds]}: runs one player speech capture (roadmap A10)
 * for the command's player and prints the transcript. For checking the capture in-world.
 */
public class DebugSpeechCaptureCommand {
    private DebugSpeechCaptureCommand() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("speech_capture")
                .executes(ctx -> execute(ctx.getSource(), 10))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 30))
                        .executes(ctx -> execute(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));
    }

    private static int execute(CommandSourceStack source, int seconds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("mc_talking.debug.speech_capture.players_only"));
            return 0;
        }
        var server = source.getServer();
        PlayerSpeechCapture.capture(player, Duration.ofSeconds(seconds)).thenAccept(result -> server.execute(() -> {
            if (result.status() == SpeechCaptureResult.Status.TRANSCRIBED) {
                source.sendSuccess(() -> Component.translatable("mc_talking.debug.speech_capture.transcript",
                        result.transcript(), result.speech().toMillis()), false);
            } else {
                source.sendFailure(Component.translatable("mc_talking.debug.speech_capture.failed",
                        result.status().name(), result.detail() == null ? "" : result.detail()));
            }
        }));
        source.sendSuccess(() -> Component.translatable("mc_talking.debug.speech_capture.started", seconds)
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
