package me.sshcrack.mc_talking.internal.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmSpeechDetectorTest {
    @Test
    void silenceAndLowLevelNoiseDoNotQualifyAsSpeech() {
        assertFalse(PcmSpeechDetector.isSpeechCandidate(new short[960]));
        short[] noise = new short[960];
        for (int i = 0; i < noise.length; i++) noise[i] = (short) ((i % 11) - 5);
        assertFalse(PcmSpeechDetector.isSpeechCandidate(noise));
    }

    @Test
    void sustainedDecodedVoiceLevelSignalQualifies() {
        short[] voice = new short[960];
        for (int i = 0; i < voice.length; i++) voice[i] = (short) (i % 2 == 0 ? 1800 : -1800);
        assertTrue(PcmSpeechDetector.isSpeechCandidate(voice));
    }

    @Test
    void isolatedImpulseDoesNotQualifyAsSpeech() {
        short[] click = new short[960];
        click[300] = 12000;
        assertFalse(PcmSpeechDetector.isSpeechCandidate(click));
    }
}
