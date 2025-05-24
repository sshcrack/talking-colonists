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
        return MineColoniesTalkingCitizens.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        MineColoniesTalkingCitizens.LOGGER.info("Initializing Voicechat Plugin");
    }

    public void onServerStart(VoicechatServerStartedEvent event) {
        MineColoniesTalkingCitizens.LOGGER.info("Voicechat Server Started");
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
        LivingEntity entity = MineColoniesTalkingCitizens.activeEntity.get(player.getUUID());
        if (entity == null) {
            return;
        }

        var manager = MineColoniesTalkingCitizens.clients.get(entity.getUUID());
        if (manager == null) {
            return;
        }

        // Process the voice packet
        manager.promptAudioOpus(packet.getOpusEncodedData());

        // Cancel previous silence task if it exists
        //cancelSilenceTask(entity.getUUID());

        // Schedule silence to be sent after the voice packet
        //scheduleSilenceTask(entity.getUUID(), manager);
    }


    private void scheduleSilenceTask(UUID entityId, TalkingManager manager) {
        // Calculate how many silence packets to send over the duration
        int packetsToSend = (int) (SILENCE_DURATION_MS / SILENCE_INTERVAL_MS);

        // Schedule a task that will send silence packets
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            manager.promptAudioRaw(new short[960]);
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
