package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Runtime service for player speech capture (roadmap A10). */
public interface PlayerSpeechService {
    @NotNull CompletableFuture<SpeechCaptureResult> capture(@NotNull ServerPlayer player, @NotNull Duration maxDuration);

    boolean cancel(@NotNull ServerPlayer player);

    boolean isCapturing(@NotNull ServerPlayer player);
}
