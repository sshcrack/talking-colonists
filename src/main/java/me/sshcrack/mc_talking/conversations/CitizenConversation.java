package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.util.AudioHelper;
import net.minecraft.server.MinecraftServer;
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

    private LocationalAudioChannel channel;

    private volatile AudioPlayer player;
    private volatile OpusEncoder encoder;

    private Consumer<ConversationState> onStateChanged;


    public enum ConversationState {
        GENERATING,
        PLAYING_AUDIO,
        ENDED
    }

    public CitizenConversation(MinecraftServer server, List<AbstractEntityCitizen> participants) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Conversation must have at least one participant");
        }

        this.participants = participants;

        new Thread(() -> {
            McTalking.LOGGER.info("Starting conversation thread for {} participants...", participants.size());
            Gson gson = new Gson();
            Type listType = new TypeToken<java.util.List<GeminiTTS.AudioChunk>>() {}.getType();
            Path debugPath = Paths.get("audio_debug.json");

            List<GeminiTTS.AudioChunk> audio = null;

            if (Files.exists(debugPath)) {
                try {
                    String json = Files.readString(debugPath);
                    audio = gson.fromJson(json, listType);
                    McTalking.LOGGER.info("Loaded conversation audio from audio_debug.json");
                } catch (IOException | JsonSyntaxException e) {
                    McTalking.LOGGER.error("Failed to read/parse audio_debug.json: {}", e.getMessage());
                }
            }

            if (audio == null) {
                try {
                    audio = CitizenConversationGenerator.generateConversation(participants, server);
                } catch (ConversationGenerationException e) {
                    McTalking.LOGGER.error("Failed to generate conversation audio: {}, original cause: {}", e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "none");
                    setState(ConversationState.ENDED);
                    return;
                }

                try {
                    String out = gson.toJson(audio, listType);
                    Files.writeString(debugPath, out);
                    McTalking.LOGGER.info("Wrote generated conversation audio to audio_debug.json");
                } catch (IOException e) {
                    McTalking.LOGGER.error("Failed to write audio_debug.json: {}", e.getMessage());
                }
            }

            synchronized (this) {
                if (getState() == ConversationState.ENDED) {
                    McTalking.LOGGER.info("Conversation aborted during generation, skipping playback");
                    return;
                }
            }

            List<GeminiTTS.AudioChunk> finalAudio = audio;
            server.execute(() -> performConversation(finalAudio));
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


        Vec3 avg = sum.scale(1.0d / participants.size());
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
        audioChannel.setDistance(Math.max(maxDistance, audioChannel.getDistance()));

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
        McTalking.LOGGER.info("Conversation state changed to {}", newState);
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
