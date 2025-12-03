package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.McTalking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class TalkingManager {
    private static final DefaultArtifactVersion FIXED_VERSION = new DefaultArtifactVersion("1.21.1-2.5.31");

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static final boolean IS_BUG_FIXED_VC = ModList.get().getModContainerById("voicechat").get().getModInfo().getVersion().compareTo(FIXED_VERSION) >= 0;

    static {
        McTalking.LOGGER.info("Compatibility check for voicechat, is bug fixed: {}", IS_BUG_FIXED_VC);
    }

    GeminiWsClient client;
    EntityAudioChannel channel;
    AbstractEntityCitizen entity;
    OpusDecoder decoder;
    private final UUID sessionId;

    public TalkingManager(AbstractEntityCitizen entity, ServerPlayer initialPlayer) {
        McTalking.LOGGER.info("Creating TalkingManager for entity: {}", entity.getStringUUID());
        if (vcApi == null) throw new IllegalStateException("Voicechat API is not initialized");

        this.entity = entity;
        this.sessionId = entity.getUUID();

        // Acquire a session slot from the central manager
        if (!SessionManager.acquireSession(sessionId, this::close)) {
            throw new IllegalStateException("Cannot create TalkingManager: session limit reached");
        }

        var uuid = IS_BUG_FIXED_VC ? UUID.randomUUID() : entity.getUUID();
        channel = vcApi.createEntityAudioChannel(uuid, vcApi.fromEntity(entity));
        if (channel == null) {
            SessionManager.releaseSession(sessionId);
            throw new IllegalStateException("Failed to create audio channel for entity: " + entity.getStringUUID());
        }

        channel.setWhispering(true);
        client = new GeminiWsClient(this, initialPlayer);
        decoder = vcApi.createDecoder();
    }

    public void promptAudioRaw(short[] raw) {
        client.batchAudio(raw);
    }

    public void promptAudioOpus(byte[] audio) {
        var raw = decoder.decode(audio);
        client.batchAudio(raw);
    }

    public void close() {
        // Release the session slot
        SessionManager.releaseSession(sessionId);
        
        client.close();
        channel = null;
    }

    /**
     * Sends a system text prompt to the AI client.
     * This can be used to provide context or instructions to the AI.
     * 
     * @param newStatusPrompt The text prompt to send
     */
    public void promptSystemText(String newStatusPrompt) {
        client.addSystemText(newStatusPrompt);
    }
}
