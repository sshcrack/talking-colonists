package me.sshcrack.mc_talking;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import me.sshcrack.mc_talking.manager.TalkingManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static me.sshcrack.mc_talking.manager.GeminiStream.FRAME_SIZE_SAMPLES;

@SuppressWarnings("unused")
@ForgeVoicechatPlugin
public class McTalkingVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi vcApi;

    // Map to track silence packet futures for each entity
    private final Map<UUID, ScheduledFuture<?>> silenceTimeouts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Silence duration in milliseconds
    private static final long SILENCE_DURATION_MS = 1000;
    // Interval at which to send silence packets (ms)
    private static final long SILENCE_INTERVAL_MS = 20;

    @Override
    public String getPluginId() {
        return MinecoloniesTalkingCitizens.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        MinecoloniesTalkingCitizens.LOGGER.info("Initializing Voicechat Plugin");
    }

    public void onServerStart(VoicechatServerStartedEvent event) {
        MinecoloniesTalkingCitizens.LOGGER.info("Voicechat Server Started");
        vcApi = event.getVoicechat();
    }


    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::handleMicPacket);
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStart);
    }

    public void handleMicPacket(MicrophonePacketEvent event) {
        var sender = event.getSenderConnection();
        if (sender == null)
            return;

        var packet = event.getPacket();
        if (packet.isWhispering())
            return;

        if (sender.isDisabled())
            return;


        var vcPlayer = sender.getPlayer();
        if (sender.isInGroup() && !Config.respondInGroups)
            return;

        var player = (ServerPlayer) vcPlayer.getPlayer();
        LivingEntity entity = MinecoloniesTalkingCitizens.activeEntity.get(player.getUUID());
        if (entity == null) {
            return;
        }

        var manager = MinecoloniesTalkingCitizens.clients.get(entity.getUUID());
        if (manager == null) {
            return;
        }

        // Process the voice packet
        manager.promptAudioOpus(packet.getOpusEncodedData());

        // Cancel previous silence task if it exists
        cancelSilenceTask(entity.getUUID());

        // Schedule silence to be sent after the voice packet
        scheduleSilenceTask(entity.getUUID(), manager);
    }

    private static final byte[] SILENCE = {-8, -41, -121, 69, 27, -124, -74, -50, 34, -63, -76, -35, 108, -74, -39, -125, 38, -77, 1, 110, 47, 58, -121, -35, -102, 126, -24, 111, 28, 78, -108, -120, -28, 79, 11, 62, 121, 27, -26, -108, -119, -128, -30, -2, 126, -32, -89, 51, 79, 65, 6, 82, 24, -60, -24, 47, -111, -46, 51, 52, -37, 0, -30, -127, 99, 27, -66, -105, 25, 80, -108, -92, 60, 98, -9, -120, 124, 117, 23, -124, -30, 73, 8, 2, 58, -56, -19, -67, 37, 80, 117, 19, -44, -60, -80, 34, -65, 85, 72, 78, 85, -90, 89, 107, -46, -110, 70, 59, 99, 122, 46, 5, 113};

    private void scheduleSilenceTask(UUID entityId, TalkingManager manager) {
        // Calculate how many silence packets to send over the duration
        int packetsToSend = (int) (SILENCE_DURATION_MS / SILENCE_INTERVAL_MS);

        // Schedule a task that will send silence packets
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            // Send silence frame
            manager.promptAudioOpus(SILENCE);
        }, 0, SILENCE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Schedule a task to stop sending silence after the duration
        executor.schedule(() -> {
            future.cancel(false);
            silenceTimeouts.remove(entityId);
        }, SILENCE_DURATION_MS, TimeUnit.MILLISECONDS);

        // Store the future so we can cancel it if needed
        silenceTimeouts.put(entityId, future);
    }

    private void cancelSilenceTask(UUID entityId) {
        ScheduledFuture<?> future = silenceTimeouts.remove(entityId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
