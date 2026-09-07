package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.config.AvailableAI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceSelectionServiceTest {
    private static final UUID ACHIRD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000019");
    private static final String TTS_MODEL = "gemini-3.1-flash-tts-preview";

    @BeforeEach
    void clearSelections() {
        VoiceSelectionService.clear();
        VoiceSelectionService.resetClockForTests();
    }

    @AfterEach
    void resetSelections() {
        VoiceSelectionService.clear();
        VoiceSelectionService.resetClockForTests();
    }

    @Test
    void explicitLiveRejectionUsesStableFallbackOnlyForThatBackendAndModel() {
        assertEquals("Achird", selectLive(AvailableAI.Flash3, ACHIRD_UUID));

        assertTrue(VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "No matching speaker voice found"));

        assertEquals("Zubenelgenubi", selectLive(AvailableAI.Flash3, ACHIRD_UUID));
        assertEquals("Achird", selectLive(AvailableAI.Flash2_5, ACHIRD_UUID));
        assertEquals("Achird", VoiceSelectionService.select(
                VoiceSelectionService.Backend.TTS,
                AvailableAI.Flash3.getName(),
                AvailableAI.Flash3,
                ACHIRD_UUID,
                false));
    }

    @Test
    void unrelated1007AuthenticationQuotaAndNetworkFailuresDoNotLearnExclusions() {
        assertFalse(VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "invalid argument: session token is stale"));
        assertFalse(VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "Achird",
                1008,
                "permission denied: api key invalid"));

        String explicitVoiceBody = errorBody("No matching speaker voice found: Achird");
        assertFalse(VoiceSelectionService.noteTtsRejected(
                AvailableAI.Flash3, TTS_MODEL, "Achird", 401, explicitVoiceBody));
        assertFalse(VoiceSelectionService.noteTtsRejected(
                AvailableAI.Flash3, TTS_MODEL, "Achird", 429, explicitVoiceBody));
        assertFalse(VoiceSelectionService.noteTtsRejected(
                AvailableAI.Flash3, TTS_MODEL, "Achird", 503, explicitVoiceBody));

        assertEquals(0, VoiceSelectionService.exclusionCountForTests());
        assertEquals("Achird", selectLive(AvailableAI.Flash3, ACHIRD_UUID));
    }

    @Test
    void ttsRejectionRequiresStructuredVoiceSpecificEvidence() {
        assertTrue(VoiceSelectionService.isExplicitTtsVoiceRejection(
                400,
                errorBody("No matching speaker voice found: Achird"),
                "Achird"));
        assertFalse(VoiceSelectionService.isExplicitTtsVoiceRejection(
                400,
                errorBody("Invalid request: temperature is out of range"),
                "Achird"));
        assertFalse(VoiceSelectionService.isExplicitTtsVoiceRejection(
                400,
                "No matching speaker voice found: Achird",
                "Achird"));
        assertFalse(VoiceSelectionService.isExplicitTtsVoiceRejection(
                400,
                "{not-json",
                "Achird"));
        assertFalse(VoiceSelectionService.isExplicitTtsVoiceRejection(
                400,
                errorBody("No matching speaker voice found: Puck"),
                "Achird"));
    }

    @Test
    void explicitTtsRejectionUsesFallbackWithoutPoisoningLive() {
        assertTrue(VoiceSelectionService.noteTtsRejected(
                AvailableAI.Flash3,
                TTS_MODEL,
                "Achird",
                400,
                errorBody("No matching speaker voice found: Achird")));

        assertEquals("Zubenelgenubi", VoiceSelectionService.select(
                VoiceSelectionService.Backend.TTS,
                TTS_MODEL,
                AvailableAI.Flash3,
                ACHIRD_UUID,
                false));
        assertEquals("Achird", selectLive(AvailableAI.Flash3, ACHIRD_UUID));
        assertEquals("Achird", VoiceSelectionService.select(
                VoiceSelectionService.Backend.TTS,
                "different-tts-model",
                AvailableAI.Flash3,
                ACHIRD_UUID,
                false));
    }

    @Test
    void expiryAndExplicitResetRestorePreferredVoice() {
        AtomicLong now = new AtomicLong(10_000L);
        VoiceSelectionService.setClockForTests(now::get);

        VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "Unsupported speaker voice");
        assertEquals("Zubenelgenubi", selectLive(AvailableAI.Flash3, ACHIRD_UUID));

        now.addAndGet(VoiceSelectionService.REJECTION_TTL_MS + 1L);
        assertEquals("Achird", selectLive(AvailableAI.Flash3, ACHIRD_UUID));
        assertEquals(0, VoiceSelectionService.exclusionCountForTests());

        VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "Speaker voice not found");
        VoiceSelectionService.clear();
        assertEquals("Achird", selectLive(AvailableAI.Flash3, ACHIRD_UUID));
    }

    @Test
    void candidateExhaustionProducesUsefulTerminalDiagnostic() {
        for (String voice : AvailableAI.Flash3.getVoiceCandidates(false)) {
            assertTrue(VoiceSelectionService.noteLiveRejected(
                    AvailableAI.Flash3,
                    voice,
                    1007,
                    "No matching speaker voice found"));
        }

        VoiceSelectionService.VoiceCandidatesExhaustedException error = assertThrows(
                VoiceSelectionService.VoiceCandidatesExhaustedException.class,
                () -> selectLive(AvailableAI.Flash3, ACHIRD_UUID));

        assertEquals(VoiceSelectionService.Backend.LIVE, error.backend());
        assertEquals(AvailableAI.Flash3.getName(), error.model());
        assertEquals("Achird", error.preferredVoice());
        assertEquals(AvailableAI.Flash3.getVoiceCandidates(false).size(), error.candidateCount());
        assertTrue(error.getMessage().contains("backend=live"));
        assertTrue(error.getMessage().contains("model=" + AvailableAI.Flash3.getName()));
    }

    @Test
    void unknownOrMalformedVoiceEvidenceCannotCorruptRecoveryState() {
        assertFalse(VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "DefinitelyNotARealVoice",
                1007,
                "No matching speaker voice found"));
        assertFalse(VoiceSelectionService.noteTtsRejected(
                AvailableAI.Flash3,
                TTS_MODEL,
                "DefinitelyNotARealVoice",
                400,
                errorBody("No matching speaker voice found: DefinitelyNotARealVoice")));
        assertEquals(0, VoiceSelectionService.exclusionCountForTests());
    }

    @Test
    void concurrentSelectionAndLearningRemainStable() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                calls.add(() -> {
                    VoiceSelectionService.noteLiveRejected(
                            AvailableAI.Flash3,
                            "Achird",
                            1007,
                            "No matching speaker voice found");
                    return selectLive(AvailableAI.Flash3, ACHIRD_UUID);
                });
            }

            for (var future : pool.invokeAll(calls)) {
                assertEquals("Zubenelgenubi", future.get());
            }
            assertEquals(1, VoiceSelectionService.exclusionCountForTests());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectionNeverChangesCitizensDeterministicPreferredVoice() {
        UUID puckCitizen = uuidForPreferred(AvailableAI.Flash3, "Puck");
        assertEquals("Achird", AvailableAI.Flash3.getRandomVoice(ACHIRD_UUID, false));
        assertEquals("Puck", AvailableAI.Flash3.getRandomVoice(puckCitizen, false));

        VoiceSelectionService.noteLiveRejected(
                AvailableAI.Flash3,
                "Achird",
                1007,
                "No matching speaker voice found");

        assertEquals("Achird", AvailableAI.Flash3.getRandomVoice(ACHIRD_UUID, false));
        assertEquals("Puck", AvailableAI.Flash3.getRandomVoice(puckCitizen, false));
        assertEquals("Puck", selectLive(AvailableAI.Flash3, puckCitizen));
    }

    private static String selectLive(AvailableAI ai, UUID citizenId) {
        return VoiceSelectionService.select(
                VoiceSelectionService.Backend.LIVE,
                ai.getName(),
                ai,
                citizenId,
                false);
    }

    private static String errorBody(String message) {
        return "{\"error\":{\"code\":400,\"message\":\"" + message + "\",\"status\":\"INVALID_ARGUMENT\"}}";
    }

    private static UUID uuidForPreferred(AvailableAI ai, String voice) {
        for (long value = 0; value < 100_000; value++) {
            UUID candidate = new UUID(0L, value);
            if (voice.equals(ai.getRandomVoice(candidate, false))) return candidate;
        }
        throw new AssertionError("Could not find deterministic UUID for voice " + voice);
    }
}
