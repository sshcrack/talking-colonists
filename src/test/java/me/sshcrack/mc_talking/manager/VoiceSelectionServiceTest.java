package me.sshcrack.mc_talking.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import me.sshcrack.mc_talking.config.AvailableAI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceSelectionServiceTest {
    private static final UUID ACHIRD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000019");

    @BeforeEach
    @AfterEach
    void clearSelections() {
        VoiceSelectionService.clear();
    }

    @Test
    void explicitRejectedVoiceUsesStableFallbackOnlyForThatModel() {
        assertEquals("Achird", VoiceSelectionService.select(AvailableAI.Flash3, ACHIRD_UUID, false));

        VoiceSelectionService.noteRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "No matching speaker voice found");

        assertEquals("Zubenelgenubi", VoiceSelectionService.select(AvailableAI.Flash3, ACHIRD_UUID, false));
        assertEquals("Achird", VoiceSelectionService.select(AvailableAI.Flash2_5, ACHIRD_UUID, false));
    }

    @Test
    void unrelated1007DoesNotBlocklistVoice() {
        VoiceSelectionService.noteRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "invalid argument: session token is stale");

        assertEquals("Achird", VoiceSelectionService.select(AvailableAI.Flash3, ACHIRD_UUID, false));
    }

    @Test
    void recognitionRequiresNarrowVoiceReasonAnd1007() {
        assertTrue(VoiceSelectionService.isExplicitVoiceRejection(1007, "No matching speaker voice found"));
        assertFalse(VoiceSelectionService.isExplicitVoiceRejection(1008, "No matching speaker voice found"));
        assertFalse(VoiceSelectionService.isExplicitVoiceRejection(1007, "The operation was aborted"));
        assertFalse(VoiceSelectionService.isExplicitVoiceRejection(1007, null));
    }
}
