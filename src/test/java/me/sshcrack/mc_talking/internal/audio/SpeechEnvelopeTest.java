package me.sshcrack.mc_talking.internal.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SpeechEnvelopeTest {
    @AfterEach void clear() { SpeechEnvelope.clear(); }
    @Test void silentAudioAndEndedTransmissionCloseMouth() {
        UUID id = UUID.randomUUID();
        SpeechEnvelope.accept(id, new short[]{12000, -12000}, 0);
        assertTrue(SpeechEnvelope.opening(id, 0) > 0);
        SpeechEnvelope.accept(id, new short[0], 1);
        assertEquals(0, SpeechEnvelope.opening(id, 1));
        SpeechEnvelope.accept(id, new short[]{3, -3}, 2);
        assertEquals(0, SpeechEnvelope.opening(id, 2));
    }
    @Test void audioIsBoundedIsolatedAndExpiresWithoutAnotherPacket() {
        UUID id = UUID.randomUUID();
        short[] audio = {32767, -32768};
        SpeechEnvelope.accept(id, audio, 0);
        audio[0] = 0;
        assertEquals(1, SpeechEnvelope.opening(id, 0));
        assertEquals(0, SpeechEnvelope.opening(UUID.randomUUID(), 0));
        assertTrue(SpeechEnvelope.opening(id, 80_000_000L) < 1);
        assertEquals(0, SpeechEnvelope.opening(id, 150_000_000L));
        SpeechEnvelope.remove(id);
        assertEquals(0, SpeechEnvelope.opening(id, 0));
    }
}
