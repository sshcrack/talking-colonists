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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;

import static me.sshcrack.mc_talking.config.McTalkingConfig.CONFIG;

@SuppressWarnings("unused")
@ForgeVoicechatPlugin
public class McTalkingVoicechatPlugin implements VoicechatPlugin {
    public static VoicechatServerApi vcApi;

    // Map to track silence packet futures for each entity
    private final Map<UUID, ScheduledFuture<?>> silenceTimeouts = new ConcurrentHashMap<>();
    // Map to track voice activity for each entity
    private final Map<UUID, Long> lastVoiceActivity = new ConcurrentHashMap<>();
    // Map to track when we've detected the end of speech for an entity
    private final Map<UUID, Long> speechEndDetections = new ConcurrentHashMap<>();
    // Map to track consecutive silent packets for each entity
    private final Map<UUID, Integer> consecutiveSilentPackets = new ConcurrentHashMap<>();
    // Whether this entity is currently in a speaking state
    private final Map<UUID, Boolean> isSpeaking = new ConcurrentHashMap<>();

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Silence duration in milliseconds
    private static final long SILENCE_DURATION_MS = 1500;
    // Interval at which to send silence packets (ms)
    private static final long SILENCE_INTERVAL_MS = 20;
    // Threshold for voice inactivity before sending silence (ms)
    private static final long VOICE_INACTIVITY_THRESHOLD_MS = 150;
    // Number of consecutive silent packets before considering speech ended
    private static final int SPEECH_END_THRESHOLD = 10;
    // Periodic silence check interval (ms)
    private static final long PERIODIC_SILENCE_CHECK_MS = 100;

    @Override
    public String getPluginId() {
        return McTalking.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        McTalking.LOGGER.info("Initializing Voicechat Plugin");

        // Start a periodic task to check for speech end and schedule silence packets
        executor.scheduleAtFixedRate(this::checkForSpeechEnd,
                PERIODIC_SILENCE_CHECK_MS, PERIODIC_SILENCE_CHECK_MS, TimeUnit.MILLISECONDS);
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
        if (sender.isInGroup() && !CONFIG.respondInGroups.get())
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
        long currentTime = System.currentTimeMillis();

        // Forward the voice data to the AI regardless of silence detection
        manager.promptAudioOpus(opusData);

        // Update voice activity tracking
        if (hasVoiceActivity) {
            // Reset consecutive silent packet counter
            consecutiveSilentPackets.put(entityId, 0);

            // Mark as speaking if not already
            isSpeaking.put(entityId, true);

            // Update last voice activity timestamp
            lastVoiceActivity.put(entityId, currentTime);

            // Cancel any existing silence tasks when there's new voice
            cancelSilenceTask(entityId);
        } else {
            // Increment consecutive silent packet counter
            int silentCount = consecutiveSilentPackets.getOrDefault(entityId, 0) + 1;
            consecutiveSilentPackets.put(entityId, silentCount);

            // If we've reached the threshold, mark the speech as potentially ended
            if (silentCount >= SPEECH_END_THRESHOLD && isSpeaking.getOrDefault(entityId, false)) {
                speechEndDetections.put(entityId, currentTime);
            }
        }
    }

    /**
     * Checks if the opus data contains voice activity
     *
     * @param opusData The opus encoded audio data
     * @return true if there's voice activity, false otherwise
     */
    private boolean hasVoiceActivity(byte[] opusData) {
        // Empty packets are definitely silence
        if (opusData == null || opusData.length <= 1) {
            return false;
        }

        // For Opus packets, the TOC byte contains information about the frame type
        // TOC byte structure: [Opus mode (2 bits)][Frame Config (3 bits)][Bandwidth (3 bits)]
        // The first 2 bits indicate the mode: 0 = SILK-only, 1 = Hybrid, 2 = CELT-only
        // Silent or very low volume frames typically have certain patterns

        // This is a heuristic approach - silent frames are typically much smaller
        // than frames with voice content
        int minimumVoicePacketSize = 10; // Adjust based on testing

        return opusData.length > minimumVoicePacketSize;
    }

    private void scheduleSilenceTask(UUID entityId, TalkingManager manager) {
        // Only send silence if a silence task is not already running for this entity
        if (silenceTimeouts.containsKey(entityId)) {
            return;
        }

        McTalking.LOGGER.info("Scheduling silence packets for entity {}", entityId);

        // Schedule a task that will send silence packets
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
            // Generate low volume ambient noise instead of pure silence
            short[] ambientAudio = generateAmbientNoise(960);
            manager.promptAudioRaw(ambientAudio);
        }, 0, SILENCE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Schedule a task to stop sending silence after the duration
        executor.schedule(() -> {
            McTalking.LOGGER.info("Stopping silence packets for entity {}", entityId);
            cancelSilenceTask(entityId);

            // Clean up tracking maps
            lastVoiceActivity.remove(entityId);
            speechEndDetections.remove(entityId);
            consecutiveSilentPackets.remove(entityId);
        }, SILENCE_DURATION_MS, TimeUnit.MILLISECONDS);

        // Store the future so we can cancel it if needed
        silenceTimeouts.put(entityId, future);
    }

    /**
     * Generates a realistic ambient noise to simulate the natural background
     * audio that would be present in a microphone stream
     *
     * @param length The length of the audio buffer to generate
     * @return A short array containing the generated audio
     */
    private short[] generateAmbientNoise(int length) {
        short[] audio = new short[length];
        Random random = new Random();

        // Parameters for realistic ambient noise
        double baseAmplitude = 5.0;  // Very low base amplitude

        // Add some very low white noise
        for (int i = 0; i < length; i++) {
            // Generate Gaussian noise (more natural sounding than uniform)
            double noise = random.nextGaussian() * baseAmplitude;
            audio[i] = (short) noise;
        }

        return audio;
    }

    private void cancelSilenceTask(UUID entityId) {
        ScheduledFuture<?> future = silenceTimeouts.remove(entityId);
        if (future != null) {
            future.cancel(true);
        }
    }

    /**
     * Periodically checks if any speaking player has ended their speech
     * and schedules silence packets accordingly
     */
    private void checkForSpeechEnd() {
        long currentTime = System.currentTimeMillis();

        // Check each entity with detected speech end
        for (Map.Entry<UUID, Long> entry : new HashMap<>(speechEndDetections).entrySet()) {
            UUID entityId = entry.getKey();
            Long speechEndTime = entry.getValue();

            // If we've exceeded the inactivity threshold and we're not already sending silence
            if (speechEndTime != null &&
                    (currentTime - speechEndTime >= VOICE_INACTIVITY_THRESHOLD_MS) &&
                    isSpeaking.getOrDefault(entityId, false) &&
                    !silenceTimeouts.containsKey(entityId)) {

                // Mark the entity as no longer speaking
                isSpeaking.put(entityId, false);

                // Clean up the speech end detection entry
                speechEndDetections.remove(entityId);

                // Get the talking manager for this entity
                var manager = ConversationManager.getClientForEntity(entityId);
                if (manager != null) {
                    // Schedule silence packets
                    scheduleSilenceTask(entityId, manager);
                }
            }
        }

        // Also check for any entities with recent voice activity but no recent packets
        for (Map.Entry<UUID, Long> entry : new HashMap<>(lastVoiceActivity).entrySet()) {
            UUID entityId = entry.getKey();
            Long lastActivity = entry.getValue();

            // If not in the speech end detection map and not already sending silence
            if (!speechEndDetections.containsKey(entityId) &&
                    !silenceTimeouts.containsKey(entityId) &&
                    isSpeaking.getOrDefault(entityId, false)) {

                // If we've exceeded the inactivity threshold
                if (lastActivity != null && (currentTime - lastActivity >= VOICE_INACTIVITY_THRESHOLD_MS * 3)) {
                    // Mark the entity as no longer speaking
                    isSpeaking.put(entityId, false);

                    // Get the talking manager for this entity
                    var manager = ConversationManager.getClientForEntity(entityId);
                    if (manager != null) {
                        // Schedule silence packets
                        scheduleSilenceTask(entityId, manager);
                    }
                }
            }
        }
    }
}
