package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.audio.CitzienEntityAudioProvider;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;

public class PregenPlayback {
    private PregenPlayback() {
        /* This utility class should not be instantiated */
    }

    public static boolean playAudioIfPossible(AbstractEntityCitizen citizen, AudioChunk audioData) {
        //NOTICE We are claiming a websocket slot here, even if we aren't using it to prevent the start of other conversations etc.
        if (!ConversationManager.hasLowPriorityCapacity(1))
            return false;

        if (ConversationManager.claimSlot(citizen, false)) {
            return false;
        }

        McTalking.LOGGER.info("Playing back pregenerated audio for {}", citizen.getCitizenData().getName());
        AudioProvider audioProvider = new CitzienEntityAudioProvider(citizen, null);
        AudioChannel channel = audioProvider.createChannel();
        if (channel == null) {
            ConversationManager.releaseSlot(citizen);
            return false;
        }

        if (audioData == null || audioData.audioBytes().length == 0) {
            ConversationManager.releaseSlot(citizen);
            return false;
        }

        GeminiStream stream = new GeminiStream(channel);

        var isFemale = citizen.getCitizenData().isFemale();
        var isChild = citizen.getCitizenData().isChild();
        if (isChild && !isFemale) {
            stream.setPitch(1.2f);
        }

        stream.setOnPause(() -> {
            stream.close();
            ConversationManager.releaseSlot(citizen);
        });

        stream.addGeminiPcmWithPitch(audioData.audioBytes(), audioData.sampleRate());
        stream.flushAudio();
        return true;
    }
}
