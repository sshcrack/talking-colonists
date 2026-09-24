package me.sshcrack.mc_talking.internal.audio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceDuckingTest {
    private static final UUID PARTNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @AfterEach
    void reset() {
        VoiceDucking.clear();
    }

    private static short[] loud() {
        short[] pcm = new short[960];
        Arrays.fill(pcm, (short) 10_000);
        return pcm;
    }

    @Test
    void otherCitizensFadeDownWhileTalkingAndBackAfter() {
        short[] last = null;
        for (int i = 0; i < 5; i++) last = VoiceDucking.apply(OTHER, true, PARTNER, 0.3F, loud());
        assertEquals(3_000, last[last.length - 1]);
        assertEquals(3_000, last[0]);

        short[] first = VoiceDucking.apply(OTHER, true, null, 0.3F, loud());
        assertTrue(first[0] < first[first.length - 1], "fades back up across the frame");
        for (int i = 0; i < 5; i++) last = VoiceDucking.apply(OTHER, true, null, 0.3F, loud());
        assertArrayEquals(loud(), last);
    }

    @Test
    void partnerPlayersAndDisabledDuckingStayFull() {
        assertArrayEquals(loud(), VoiceDucking.apply(PARTNER, true, PARTNER, 0.3F, loud()));
        assertArrayEquals(loud(), VoiceDucking.apply(OTHER, false, PARTNER, 0.3F, loud()));
        assertArrayEquals(loud(), VoiceDucking.apply(OTHER, true, PARTNER, 1F, loud()));
    }

    @Test
    void rampIsLinearAcrossTheFrame() {
        short[] pcm = VoiceDucking.scale(new short[]{1000, 1000, 1000}, 1F, 0F);
        assertArrayEquals(new short[]{1000, 500, 0}, pcm);
    }
}
