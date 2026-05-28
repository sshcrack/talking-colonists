package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PregenPlayback {
    private PregenPlayback() {
        /* This utility class should not be instantiated */
    }

    public static boolean playAudioIfPossible(AbstractEntityCitizen citizen, AudioChunk audioData) {
        if (ConversationManager.isCitizenBusy(citizen) || ConversationManager.isCitizenOnCooldown(citizen))
            return false;


        McTalking.LOGGER.info("Playing back pregenerated audio for {}", citizen.getCitizenData().getName());
        AudioProvider audioProvider = new CitzienEntityAudioProvider(citizen, null);
        AudioChannel channel = audioProvider.createChannel();
        if (channel == null) {
            return false;
        }

        if (audioData == null || audioData.audioBytes().length == 0) {
            return false;
        }

        GeminiStream stream = new GeminiStream(channel);

        var isFemale = citizen.getCitizenData().isFemale();
        var isChild = citizen.getCitizenData().isChild();
        if (isChild && !isFemale) {
            stream.setPitch(1.2f);
        }

        stream.setOnPause(stream::close);

        stream.addGeminiPcmWithPitch(audioData.audioBytes(), audioData.sampleRate());
        stream.flushAudio();
        return true;
    }
}
