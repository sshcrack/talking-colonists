package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.util.AudioHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE;
import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class CitizenConversation {
    private final List<AbstractEntityCitizen> participants;
    private final AtomicReference<ConversationState> state = new AtomicReference<>(ConversationState.GENERATING);

    private List<GeminiTTS.AudioChunk> conversationAudio;
    private LocationalAudioChannel channel;

    private volatile AudioPlayer player;
    private volatile OpusEncoder encoder;

    private Consumer<ConversationState> onStateChanged;


    public enum ConversationState {
        GENERATING,
        PLAYING_AUDIO,
        ENDED
    }

    public CitizenConversation(List<AbstractEntityCitizen> participants) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Conversation must have at least one participant");
        }

        this.participants = participants;

        new Thread(() -> {
            McTalking.LOGGER.info("Starting conversation thread for {} participants...", participants.size());
            List<GeminiTTS.AudioChunk> audio;
            try {
                audio = CitizenConversationGenerator.generateConversation(participants);
            } catch (CitizenConversationGenerator.ConversationGenerationException e) {
                McTalking.LOGGER.error("Failed to generate conversation audio: {}", e.getMessage());
                setState(ConversationState.ENDED);
                return;
            }

            synchronized (this) {
                if (getState() == ConversationState.ENDED) {
                    McTalking.LOGGER.info("Conversation aborted during generation, skipping playback");
                    return;
                }
            }

            performConversation(audio);
        }).start();
    }

    private void performConversation(List<GeminiTTS.AudioChunk> conversationAudio) {
        Vec3 sum = Vec3.ZERO;

        Vec3 minPos = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        Vec3 maxPos = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (AbstractEntityCitizen citizen : participants) {
            double x = citizen.getX();
            double y = citizen.getY();
            double z = citizen.getZ();
            sum = sum.add(x, y, z);

            double newMaxX = Math.max(maxPos.x, x);
            double newMaxY = Math.max(maxPos.y, y);
            double newMaxZ = Math.max(maxPos.z, z);

            double newMinX = Math.min(minPos.x, x);
            double newMinY = Math.min(minPos.y, y);
            double newMinZ = Math.min(minPos.z, z);

            maxPos = new Vec3(newMaxX, newMaxY, newMaxZ);
            minPos = new Vec3(newMinX, newMinY, newMinZ);
        }


        Vec3 avg = sum.scale(1.0d / ((double) participants.size()));
        //noinspection SequencedCollectionMethodCanBeUsed
        ServerLevel level = (ServerLevel) participants.get(0).level();

        UUID channelId = UUID.randomUUID();
        var vcLevel = vcApi.fromServerLevel(level);
        var avgPos = vcApi.createPosition(avg.x, avg.y, avg.z);

        var audioChannel = vcApi.createLocationalAudioChannel(channelId, vcLevel, avgPos);
        if (audioChannel == null) {
            McTalking.LOGGER.error("Failed to create audio channel for conversation!");
            return;
        }

        float maxDistance = (float) maxPos.subtract(minPos).length();
        audioChannel.setDistance(maxDistance);

        var converter = vcApi.getAudioConverter();
        List<short[]> chunks = conversationAudio.stream()
                .map(chunk -> {
                    short[] samples = converter.bytesToShorts(chunk.audioBytes());
                    return AudioHelper.resampleAudio(samples, chunk.sampleRate(), TARGET_SAMPLE_RATE);
                })
                .toList();

        int totalLength = chunks.stream().mapToInt(a -> a.length).sum();
        short[] rawSamples = new short[totalLength];
        int pos = 0;
        for (short[] chunk : chunks) {
            System.arraycopy(chunk, 0, rawSamples, pos, chunk.length);
            pos += chunk.length;
        }


        encoder = vcApi.createEncoder(OpusEncoderMode.AUDIO);
        AudioPlayer newPlayer = vcApi.createAudioPlayer(audioChannel, encoder, rawSamples);

        synchronized (this) {
            if (getState() == ConversationState.ENDED) {
                // Aborted while we were setting up
                encoder.close();
                return;
            }
            player = newPlayer;
            setState(ConversationState.PLAYING_AUDIO);
        }

        McTalking.LOGGER.info("Starting conversation playback at position {} with max distance {}", avg, maxDistance);
        player.startPlaying();
        player.setOnStopped(() -> {
            setState(ConversationState.ENDED);
            encoder.close();
        });
    }

    private void setState(ConversationState newState) {
        state.set(newState);
        if (onStateChanged != null) {
            onStateChanged.accept(newState);
        }
    }

    public void abortConversation() {
        synchronized (this) {
            setState(ConversationState.ENDED);
        }

        // Read after setting state — volatile guarantees visibility
        if (player != null) {
            player.stopPlaying();
        }
    }

    public void setOnStateChanged(Consumer<ConversationState> callback) {
        this.onStateChanged = callback;
    }

    public ConversationState getState() {
        return state.get();
    }
}
