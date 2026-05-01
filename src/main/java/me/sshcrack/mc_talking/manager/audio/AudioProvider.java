package me.sshcrack.mc_talking.manager.audio;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;

public interface AudioProvider {
    AudioChannel createChannel();

    OpusDecoder createDecoder();
}
