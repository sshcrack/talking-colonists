package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;

public class PregenPlayback {
    public static void playAudio(AbstractEntityCitizen citizen, byte[] audioData) {
        AudioProvider audioProvider = new CitzienEntityAudioProvider(citizen, null);
        AudioChannel channel = audioProvider.createChannel();
        if (channel == null) return;

        GeminiStream stream = new GeminiStream(channel);
        
        var isFemale = citizen.getCitizenData().isFemale();
        var isChild = citizen.getCitizenData().isChild();
        if (isChild && !isFemale) {
            stream.setPitch(1.2f);
        }

        stream.addGeminiPcmWithPitch(audioData, 48000);
        stream.flushAudio();
        
        // Wait for playback to finish? The AudioPlayer handles it asynchronously.
        // Once stopped, it stops automatically.
    }
}
