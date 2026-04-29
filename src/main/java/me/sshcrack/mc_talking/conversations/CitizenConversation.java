package me.sshcrack.mc_talking.conversations;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import me.sshcrack.mc_talking.manager.GeminiStream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class CitizenConversation {
    private final List<AbstractEntityCitizen> participants;
    private final AtomicReference<ConversationState> state = new AtomicReference<>(ConversationState.GENERATING);
    private final MinecraftServer server;

    private final GeminiStream stream;

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

        McTalking.LOGGER.info("Starting conversation generation for participants: {}", participants.stream().map(c -> c.getName().getString()).toList());
        this.participants = participants;
        this.server = server;
        this.stream = new GeminiStream(constructAudioChannel());
    }


    private LocationalAudioChannel constructAudioChannel() {
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

        var channel = vcApi.createLocationalAudioChannel(channelId, vcLevel, avgPos);
        if (channel == null) {
            McTalking.LOGGER.error("Failed to create audio channel for conversation!");
            return null;
        }

        float maxDistance = (float) maxPos.subtract(minPos).length();
        channel.setDistance(Math.max(maxDistance, channel.getDistance()));
        channel.setCategory(McTalkingVoicechatPlugin.CITIZEN_CONVERSATION);

        return channel;
    }

    public void performConversation() {
        new Thread(() -> {
            setState(ConversationState.GENERATING);
            try {
                CitizenConversationGenerator.generateConversation(participants, server, chunk -> stream.addGeminiPcmWithPitch(chunk.audioBytes(), chunk.sampleRate()));
            } catch (ConversationGenerationException e) {
                McTalking.LOGGER.error("Failed to generate conversation audio: {}, original cause: {}", e.getMessage(), e.getCause() != null ? e.getCause().getMessage() : "none");
            } finally {
                setState(ConversationState.ENDED);
            }
        }).start();
    }

    private void setState(ConversationState newState) {
        McTalking.LOGGER.info("Conversation state changed to {}", newState);
        state.set(newState);
        if (onStateChanged != null) {
            onStateChanged.accept(newState);
        }
    }

    public void setOnStateChanged(Consumer<ConversationState> callback) {
        this.onStateChanged = callback;
    }
}
