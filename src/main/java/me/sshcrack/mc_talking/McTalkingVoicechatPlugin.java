package me.sshcrack.mc_talking;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import me.sshcrack.mc_talking.config.McTalkingConfig;
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
    // Map to track voice activity for each entity
    private final Map<UUID, Long> lastVoiceActivity = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Silence duration in milliseconds
    private static final long SILENCE_DURATION_MS = 1000;
    // Interval at which to send silence packets (ms)
    private static final long SILENCE_INTERVAL_MS = 20;
    // Threshold for voice inactivity before sending silence (ms)
    private static final long VOICE_INACTIVITY_THRESHOLD_MS = 200;

    @Override
    public String getPluginId() {
        return McTalking.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        McTalking.LOGGER.info("Initializing Voicechat Plugin");
    }

    public void onServerStart(VoicechatServerStartedEvent event) {
        McTalking.LOGGER.info("Voicechat Server Started");
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
        if (sender.isInGroup() && !McTalkingConfig.respondInGroups)
            return;

        var player = (ServerPlayer) vcPlayer.getPlayer();
        LivingEntity entity = ConversationManager.getActiveEntity(player.getUUID());
        if (entity == null) {
            return;
        }

        var manager = ConversationManager.getClientForEntity(entity.getUUID());
        if (manager == null) {
            return;
        }

        UUID entityId = entity.getUUID();
        
        // Process the voice packet
        byte[] opusData = packet.getOpusEncodedData();
        boolean hasVoiceActivity = hasVoiceActivity(opusData);
        
        // Update the last voice activity timestamp
        if (hasVoiceActivity) {
            lastVoiceActivity.put(entityId, System.currentTimeMillis());
            manager.promptAudioOpus(opusData);
            
            // Cancel any existing silence tasks when there's voice
            cancelSilenceTask(entityId);
        } else {
            // Check if we need to start sending silence
            Long lastActivity = lastVoiceActivity.get(entityId);
            long currentTime = System.currentTimeMillis();
            
            if (lastActivity != null && 
                (currentTime - lastActivity > VOICE_INACTIVITY_THRESHOLD_MS) && 
                !silenceTimeouts.containsKey(entityId)) {
                // Schedule silence packets after inactivity threshold
                scheduleSilenceTask(entityId, manager);
            }
        }
    }

    /**
     * Checks if the opus data contains voice activity
     * @param opusData The opus encoded audio data
     * @return true if there's voice activity, false otherwise
     */
    private boolean hasVoiceActivity(byte[] opusData) {
        // Simple check - empty packets are definitely silence
        if (opusData == null || opusData.length <= 1) {
            return false;
        }
        
        // For more accurate detection, you'd need to analyze the audio content
        // This is a simplified approach - assuming non-empty packets contain voice
        return true;
    }    private void scheduleSilenceTask(UUID entityId, TalkingManager manager) {
        // Only send silence if a silence task is not already running for this entity
        if (silenceTimeouts.containsKey(entityId)) {
            return;
        }
        
        // Schedule a task that will send silence packets
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            // Check if there has been voice activity since we scheduled this task
            Long lastActivity = lastVoiceActivity.get(entityId);
            long currentTime = System.currentTimeMillis();
              // Only send low volume humming if we're still outside the voice activity threshold
            if (lastActivity != null && (currentTime - lastActivity > VOICE_INACTIVITY_THRESHOLD_MS)) {
                // Generate low volume humming instead of pure silence
                short[] hummingAudio = generateLowVolumeHumming(960);
                manager.promptAudioRaw(hummingAudio);
            } else {
                // There was recent voice activity, cancel this task
                cancelSilenceTask(entityId);
            }
        }, 0, SILENCE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Schedule a task to stop sending silence after the duration
        executor.schedule(() -> {
            cancelSilenceTask(entityId);
        }, SILENCE_DURATION_MS, TimeUnit.MILLISECONDS);

        // Store the future so we can cancel it if needed
        silenceTimeouts.put(entityId, future);
    }

    /**
     * Generates a low volume humming sound to keep the audio stream active
     * @param length The length of the audio buffer to generate
     * @return A short array containing the generated audio
     */
    private short[] generateLowVolumeHumming(int length) {
        short[] audio = new short[length];
        // Generate a very low amplitude sine wave (humming)
        double frequency = 110.0; // 110 Hz, a low A note
        double amplitude = 10.0;  // Very low amplitude (normal voice would be ~10000)
        double sampleRate = 48000.0; // Standard sample rate for voice chat
        
        for (int i = 0; i < length; i++) {
            double time = i / sampleRate;
            audio[i] = (short)(amplitude * Math.sin(2 * Math.PI * frequency * time));
        }
        
        return audio;
    }

    private void cancelSilenceTask(UUID entityId) {
        ScheduledFuture<?> future = silenceTimeouts.remove(entityId);
        if (future != null) {
            future.cancel(true);
        }
    }
}
