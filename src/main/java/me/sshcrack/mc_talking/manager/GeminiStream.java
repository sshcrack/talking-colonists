package me.sshcrack.mc_talking.manager;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;

/**
 * @deprecated Use {@link AiAudioPlayer} instead.
 */
@Deprecated(forRemoval = true)
public class GeminiStream extends AiAudioPlayer {
    public GeminiStream(AudioChannel channel) {
        super(channel);
    }
}
