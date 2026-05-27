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
    public static boolean playAudio(AbstractEntityCitizen citizen, AudioChunk audioData) {
        return playAudio(citizen, audioData, false);
    }

    public static boolean playAudio(AbstractEntityCitizen citizen, AudioChunk audioData, boolean alreadyClaimed) {
        if (!alreadyClaimed && !ConversationManager.claimSpeech(citizen.getUUID(), false)) {
            return false;
        }

        McTalking.LOGGER.info("Playing back pregenerated audio for {}", citizen.getCitizenData().getName());
        AudioProvider audioProvider = new CitzienEntityAudioProvider(citizen, null);
        AudioChannel channel = audioProvider.createChannel();
        if (channel == null) {
            ConversationManager.releaseSpeechClaim(citizen.getUUID());
            return false;
        }

        if (audioData == null || audioData.audioBytes().length == 0) {
            ConversationManager.releaseSpeechClaim(citizen.getUUID());
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
            ConversationManager.releaseSpeechClaim(citizen.getUUID());
        });

        stream.addGeminiPcmWithPitch(audioData.audioBytes(), audioData.sampleRate());
        stream.flushAudio();
        return true;
    }
}
