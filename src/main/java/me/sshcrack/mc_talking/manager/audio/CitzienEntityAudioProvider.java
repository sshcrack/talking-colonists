package me.sshcrack.mc_talking.manager.audio;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import me.sshcrack.mc_talking.McTalkingVoicechatPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CitzienEntityAudioProvider implements AudioProvider {
    private final AbstractEntityCitizen entity;
    @Nullable
    private final String channelId;

    public CitzienEntityAudioProvider(AbstractEntityCitizen entity, @Nullable String channelId) {
        this.entity = entity;
        this.channelId = channelId;
    }

    @Override
    public AudioChannel createChannel() {
        if (McTalkingVoicechatPlugin.vcApi == null) {
            return null;
        }

        var channel = McTalkingVoicechatPlugin.vcApi.createEntityAudioChannel(UUID.randomUUID(), McTalkingVoicechatPlugin.vcApi.fromEntity(entity));
        if (channel == null) {
            throw new IllegalStateException("Failed to create audio channel for entity: " + entity.getStringUUID());
        }

        if (channelId != null) {
            channel.setCategory(channelId);
        }

        channel.setWhispering(true);
        return channel;
    }

    @Override
    public OpusDecoder createDecoder() {
        if (McTalkingVoicechatPlugin.vcApi == null) return null;
        return McTalkingVoicechatPlugin.vcApi.createDecoder();
    }
}
