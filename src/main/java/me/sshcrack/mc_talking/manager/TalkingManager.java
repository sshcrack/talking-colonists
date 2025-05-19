package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class TalkingManager {
    GeminiWsClient client;
    EntityAudioChannel channel;
    AbstractEntityCitizen entity;
    OpusDecoder decoder;

    public TalkingManager(AbstractEntityCitizen entity, ServerPlayer initialPlayer) {
        MinecoloniesTalkingCitizens.LOGGER.info("Creating TalkingManager for entity: {}", entity.getStringUUID());
        if (vcApi == null)
            throw new IllegalStateException("Voicechat API is not initialized");

        this.entity = entity;
        channel = vcApi.createEntityAudioChannel(entity.getUUID(), vcApi.fromEntity(entity));

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
