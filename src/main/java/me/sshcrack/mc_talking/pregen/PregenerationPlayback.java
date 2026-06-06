package me.sshcrack.mc_talking.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import me.sshcrack.gemini_live_lib.misc.GeminiTTS.AudioChunk;
import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.manager.GeminiStream;
import me.sshcrack.mc_talking.manager.audio.AudioProvider;
import me.sshcrack.mc_talking.manager.audio.CitizenEntityAudioProvider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PregenerationPlayback {
    private static final Map<UUID, Boolean> ACTIVE_PREGENERATED_PLAYBACK = new ConcurrentHashMap<>();

    private PregenerationPlayback() {
        /* This utility class should not be instantiated */
    }

    private static void releasePlaybackSlot(UUID citizenId) {
        ACTIVE_PREGENERATED_PLAYBACK.remove(citizenId);
    }

    public static boolean playAudioIfPossible(AbstractEntityCitizen citizen, AudioChunk audioData) {
        if (ConversationManager.isCitizenBusy(citizen) || ConversationManager.isCitizenOnCooldown(citizen))
            return false;

        UUID citizenId = citizen.getUUID();
        if (ACTIVE_PREGENERATED_PLAYBACK.putIfAbsent(citizenId, Boolean.TRUE) != null) {
            return false;
        }

        try {
            McTalking.LOGGER.info("Playing back pregenerated audio for {}", citizen.getCitizenData().getName());
            AudioProvider audioProvider = new CitizenEntityAudioProvider(citizen, null);
            AudioChannel channel = audioProvider.createChannel();
            if (channel == null) {
                releasePlaybackSlot(citizenId);
                return false;
            }

            if (audioData == null || audioData.audioBytes().length == 0) {
                releasePlaybackSlot(citizenId);
                return false;
            }

            GeminiStream stream = new GeminiStream(channel);

            var isFemale = citizen.getCitizenData().isFemale();
            var isChild = citizen.getCitizenData().isChild();
            if (isChild && !isFemale) {
                stream.setPitch(1.2f);
            }

            stream.setOnPause(() -> {
                try {
                    stream.close();
                } finally {
                    releasePlaybackSlot(citizenId);
                }
            });

            stream.addGeminiPcmWithPitch(audioData.audioBytes(), audioData.sampleRate());
            stream.flushAudio();
            return true;
        } catch (RuntimeException e) {
            releasePlaybackSlot(citizenId);
            throw e;
        }
    }
}
