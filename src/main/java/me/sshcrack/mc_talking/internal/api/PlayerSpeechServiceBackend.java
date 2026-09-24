package me.sshcrack.mc_talking.internal.api;

import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.api.service.PlayerSpeechService;
import me.sshcrack.mc_talking.api.speech.SpeechCaptureResult;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.internal.speech.GeminiAudioTranscriber;
import me.sshcrack.mc_talking.internal.speech.SpeechCaptureRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wires {@link SpeechCaptureRuntime} to Simple Voice Chat, the Flash model and the player's action
 * bar (roadmap A10). Microphone packets arrive on the voice chat thread; the indicator is always
 * drawn on the server thread.
 */
public final class PlayerSpeechServiceBackend implements PlayerSpeechService {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mc-talking-speech-capture");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, OpusDecoder> DECODERS = new ConcurrentHashMap<>();
    private static final Map<UUID, ScheduledFuture<?>> INDICATOR_REFRESH = new ConcurrentHashMap<>();
    private static volatile @Nullable MinecraftServer server;

    static final SpeechCaptureRuntime RUNTIME = new SpeechCaptureRuntime(
            new GeminiAudioTranscriber(McTalkingConfig.FLASH_MODEL, () -> McTalkingConfig.INSTANCE.instance().geminiApiKey),
            PlayerSpeechServiceBackend::blockedReason,
            new ActionBarIndicator(),
            (task, delayNanos) -> {
                ScheduledFuture<?> future = SCHEDULER.schedule(task, Math.max(0, delayNanos), TimeUnit.NANOSECONDS);
                return () -> future.cancel(false);
            },
            System::nanoTime,
            SpeechCaptureRuntime.Settings.defaults());

    @Override
    public @NotNull CompletableFuture<SpeechCaptureResult> capture(@NotNull ServerPlayer player, @NotNull Duration maxDuration) {
        Objects.requireNonNull(player, "player");
        server = player.getServer();
        CompletableFuture<SpeechCaptureResult> result = RUNTIME.start(player.getUUID(), maxDuration);
        return result.whenComplete((outcome, error) -> reportQuota(outcome));
    }

    @Override
    public boolean cancel(@NotNull ServerPlayer player) {
        return RUNTIME.cancel(player.getUUID());
    }

    @Override
    public boolean isCapturing(@NotNull ServerPlayer player) {
        return RUNTIME.isCapturing(player.getUUID());
    }

    /**
     * Routes one microphone packet to the player's capture, if any.
     *
     * @return whether a capture owns the microphone, so the packet must not reach a conversation
     */
    public static boolean acceptMicrophoneOpus(UUID player, byte[] opus) {
        if (!RUNTIME.isCapturing(player)) return false;
        if (!RUNTIME.isListening(player)) return true;
        OpusDecoder decoder = DECODERS.computeIfAbsent(player, ignored -> McTalkingVoicechatPlugin.vcApi.createDecoder());
        short[] pcm;
        synchronized (decoder) {
            if (decoder.isClosed()) return true;
            pcm = decoder.decode(opus);
        }
        return RUNTIME.acceptMicrophone(player, pcm);
    }

    public static void onPlayerLeft(UUID player) {
        RUNTIME.playerLeft(player);
    }

    public static void onServerStopping() {
        RUNTIME.cancelAll();
        server = null;
    }

    private static @Nullable SpeechCaptureResult.Status blockedReason(UUID player) {
        if (!McTalkingConfig.hasGeminiApiKey()) return SpeechCaptureResult.Status.UNAVAILABLE;
        if (QuotaTracker.isQuotaExceeded(McTalkingConfig.FLASH_MODEL)) return SpeechCaptureResult.Status.QUOTA;
        var api = McTalkingVoicechatPlugin.vcApi;
        var connection = api == null ? null : api.getConnectionOf(player);
        if (connection == null || !connection.isConnected() || connection.isDisabled()) {
            return SpeechCaptureResult.Status.NO_VOICE_CHAT;
        }
        return null;
    }

    private static void reportQuota(@Nullable SpeechCaptureResult outcome) {
        if (outcome == null) return;
        switch (outcome.status()) {
            case QUOTA -> QuotaTracker.reportQuotaExceeded(McTalkingConfig.FLASH_MODEL);
            case TRANSCRIBED, NO_SPEECH -> {
                if (!outcome.speech().isZero()) QuotaTracker.reportSuccess(McTalkingConfig.FLASH_MODEL);
            }
            default -> {
            }
        }
    }

    private static void closeDecoder(UUID player) {
        OpusDecoder decoder = DECODERS.remove(player);
        if (decoder == null) return;
        synchronized (decoder) {
            if (!decoder.isClosed()) decoder.close();
        }
    }

    /**
     * Action-bar text above the hotbar. It fades after a few seconds, so it is redrawn every second
     * while the capture runs, and cleared at the end.
     */
    private static final class ActionBarIndicator implements SpeechCaptureRuntime.Indicator {
        @Override
        public void listening(UUID player) {
            show(player, Component.translatable("mc_talking.speech_capture.listening").withStyle(ChatFormatting.RED));
        }

        @Override
        public void transcribing(UUID player) {
            closeDecoder(player);
            show(player, Component.translatable("mc_talking.speech_capture.transcribing").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public void finished(UUID player) {
            closeDecoder(player);
            ScheduledFuture<?> refresh = INDICATOR_REFRESH.remove(player);
            if (refresh != null) refresh.cancel(false);
            onServerThread(player, target -> target.displayClientMessage(Component.empty(), true));
        }

        private static void show(UUID player, Component message) {
            ScheduledFuture<?> previous = INDICATOR_REFRESH.put(player, SCHEDULER.scheduleAtFixedRate(
                    () -> onServerThread(player, target -> target.displayClientMessage(message, true)),
                    0, 1, TimeUnit.SECONDS));
            if (previous != null) previous.cancel(false);
        }

        private static void onServerThread(UUID player, java.util.function.Consumer<ServerPlayer> action) {
            MinecraftServer current = server;
            if (current == null) return;
            current.execute(() -> {
                ServerPlayer target = current.getPlayerList().getPlayer(player);
                if (target != null) action.accept(target);
            });
        }
    }
}
