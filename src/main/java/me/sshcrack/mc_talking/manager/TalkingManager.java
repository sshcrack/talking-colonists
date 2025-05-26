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

    public TalkingManager(AbstractEntityCitizen entity, ServerPlayer initialPlayer) {
        McTalking.LOGGER.info("Creating TalkingManager for entity: {}", entity.getStringUUID());
        if (vcApi == null) throw new IllegalStateException("Voicechat API is not initialized");

        this.entity = entity;

        var uuid = IS_BUG_FIXED_VC ? UUID.randomUUID() : entity.getUUID();
        channel = vcApi.createEntityAudioChannel(uuid, vcApi.fromEntity(entity));
        if (channel == null) {
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
        client.close();
        channel = null;
    }
}
