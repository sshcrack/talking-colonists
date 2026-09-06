package me.sshcrack.mc_talking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class AvailableAITest {
    private static final UUID ACHIRD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000019");
    private static final UUID UNCHANGED_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    void typoSlotSelectsAchirdForBothModels() {
        assertEquals("Achird", AvailableAI.Flash3.getRandomVoice(ACHIRD_UUID, false));
        assertEquals("Achird", AvailableAI.Flash2_5.getRandomVoice(ACHIRD_UUID, false));
    }

    @Test
    void otherVoiceSelectionRemainsStableForBothModels() {
        assertEquals("Puck", AvailableAI.Flash3.getRandomVoice(UNCHANGED_UUID, false));
        assertEquals("Puck", AvailableAI.Flash2_5.getRandomVoice(UNCHANGED_UUID, false));
    }
}
