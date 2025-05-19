package me.sshcrack.mc_talking.manager;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.MinecoloniesTalkingCitizens;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class TalkingManager {
    GeminiWsClient client;
    LocationalAudioChannel channel;
    AbstractEntityCitizen entity;
    OpusDecoder decoder;

    public TalkingManager(AbstractEntityCitizen entity) {
        MinecoloniesTalkingCitizens.LOGGER.info("Creating TalkingManager for entity: {}", entity.getStringUUID());
        if (vcApi == null)
            throw new IllegalStateException("Voicechat API is not initialized");

        this.entity = entity;
        var vcLevel = vcApi.fromServerLevel(entity.level());
        var pos = vcApi.createPosition(entity.getX(), entity.getY(), entity.getZ());
        channel = vcApi.createLocationalAudioChannel(UUID.randomUUID(), vcLevel, pos);

        client = new GeminiWsClient(this);
        decoder = vcApi.createDecoder();
    }

    public void updatePos() {
        if (client.isClosed() || client.stream.player == null || !client.stream.player.isPlaying())
            return;

        var pos = vcApi.createPosition(entity.getX(), entity.getY(), entity.getZ());
        channel.updateLocation(pos);
    }

    public static final int OPUS_SAMPLE_RATE = 48000;


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
