package me.sshcrack.mc_talking.conversations;

import me.sshcrack.gemini_live_lib.misc.GeminiTTS;
import me.sshcrack.gemini_live_lib.misc.UnexpectedResponseException;
import me.sshcrack.mc_talking.config.AvailableAI;
import me.sshcrack.mc_talking.manager.VoiceSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsVoiceRecoveryTest {
    private static final UUID ACHIRD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000019");
    private static final String MODEL = "gemini-3.1-flash-tts-preview";

    @BeforeEach
    @AfterEach
    void clearVoiceRecovery() {
        VoiceSelectionService.clear();
    }

    @Test
    void explicitVoiceFailureRetriesOnceWithStableFallback() throws Exception {
        List<String> attempts = new ArrayList<>();

        TtsVoiceRecovery.generate(
                AvailableAI.Flash3,
                MODEL,
                List.of(new TtsVoiceRecovery.Speaker(ACHIRD_UUID, "Aldric", false)),
                voices -> {
                    String voice = voiceName(voices, 0);
                    attempts.add(voice);
                    if (attempts.size() == 1) {
                        throw rejectedVoice(voice);
                    }
                });

        assertEquals(List.of("Achird", "Zubenelgenubi"), attempts);
    }

    @Test
    void unrelatedStructured400DoesNotRetryOrLearn() {
        AtomicInteger attempts = new AtomicInteger();
        UnexpectedResponseException original = new UnexpectedResponseException(
                "provider error",
                400,
                errorBody("Invalid request: temperature is out of range"));

        UnexpectedResponseException thrown = assertThrows(
                UnexpectedResponseException.class,
                () -> TtsVoiceRecovery.generate(
                        AvailableAI.Flash3,
                        MODEL,
                        List.of(new TtsVoiceRecovery.Speaker(ACHIRD_UUID, "Aldric", false)),
                        voices -> {
                            attempts.incrementAndGet();
                            throw original;
                        }));

        assertEquals(original, thrown);
        assertEquals(1, attempts.get());
        assertEquals("Achird", selectTts());
    }

    @Test
    void authenticationQuotaAndServiceFailuresNeverRetry() {
        for (int status : List.of(401, 429, 503)) {
            VoiceSelectionService.clear();
            AtomicInteger attempts = new AtomicInteger();
            UnexpectedResponseException original = new UnexpectedResponseException(
                    "provider error",
                    status,
                    errorBody("No matching speaker voice found: Achird"));

            UnexpectedResponseException thrown = assertThrows(
                    UnexpectedResponseException.class,
                    () -> TtsVoiceRecovery.generate(
                            AvailableAI.Flash3,
                            MODEL,
                            List.of(new TtsVoiceRecovery.Speaker(ACHIRD_UUID, "Aldric", false)),
                            voices -> {
                                attempts.incrementAndGet();
                                throw original;
                            }));

            assertEquals(original, thrown);
            assertEquals(1, attempts.get());
            assertEquals("Achird", selectTts());
        }
    }


    @Test
    void networkFailureNeverRetriesOrLearns() {
        AtomicInteger attempts = new AtomicInteger();
        IOException original = new IOException("connection reset");

        IOException thrown = assertThrows(
                IOException.class,
                () -> TtsVoiceRecovery.generate(
                        AvailableAI.Flash3,
                        MODEL,
                        List.of(new TtsVoiceRecovery.Speaker(ACHIRD_UUID, "Aldric", false)),
                        voices -> {
                            attempts.incrementAndGet();
                            throw original;
                        }));

        assertEquals(original, thrown);
        assertEquals(1, attempts.get());
        assertEquals("Achird", selectTts());
    }

    @Test
    void voiceRetryLimitIsBoundedAndProducesTerminalDiagnostic() {
        AtomicInteger attempts = new AtomicInteger();
        List<String> attemptedVoices = new ArrayList<>();

        UnexpectedResponseException error = assertThrows(
                UnexpectedResponseException.class,
                () -> TtsVoiceRecovery.generate(
                        AvailableAI.Flash3,
                        MODEL,
                        List.of(new TtsVoiceRecovery.Speaker(ACHIRD_UUID, "Aldric", false)),
                        voices -> {
                            String voice = voiceName(voices, 0);
                            attemptedVoices.add(voice);
                            attempts.incrementAndGet();
                            throw rejectedVoice(voice);
                        }));

        assertEquals(TtsVoiceRecovery.MAX_FALLBACK_ATTEMPTS + 1, attempts.get());
        assertEquals(List.of("Achird", "Zubenelgenubi", "Sadachbia", "Sadaltager"), attemptedVoices);
        assertTrue(error.getMessage().contains("retry limit " + TtsVoiceRecovery.MAX_FALLBACK_ATTEMPTS));
        assertTrue(error.getMessage().contains("model=" + MODEL));
        assertTrue(error.getMessage().contains("lastVoice=Sadaltager"));
    }

    @Test
    void ambiguousMultiSpeakerRejectionDoesNotMutateOrRetry() {
        UUID puckUuid = uuidForPreferred("Puck");
        AtomicInteger attempts = new AtomicInteger();

        UnexpectedResponseException original = new UnexpectedResponseException(
                "provider error",
                400,
                errorBody("No matching speaker voice found: Achird; unsupported speaker voice Puck"));

        UnexpectedResponseException thrown = assertThrows(
                UnexpectedResponseException.class,
                () -> TtsVoiceRecovery.generate(
                        AvailableAI.Flash3,
                        MODEL,
                        List.of(
                                new TtsVoiceRecovery.Speaker(ACHIRD_UUID, "Aldric", false),
                                new TtsVoiceRecovery.Speaker(puckUuid, "Beatrice", false)),
                        voices -> {
                            attempts.incrementAndGet();
                            throw original;
                        }));

        assertEquals(original, thrown);
        assertEquals(1, attempts.get());
        assertEquals("Achird", selectTts());
        assertEquals("Puck", VoiceSelectionService.select(
                VoiceSelectionService.Backend.TTS,
                MODEL,
                AvailableAI.Flash3,
                puckUuid,
                false));
    }


    private static UnexpectedResponseException rejectedVoice(String voice) {
        return new UnexpectedResponseException(
                "provider error",
                400,
                errorBody("No matching speaker voice found: " + voice));
    }

    private static String selectTts() {
        return VoiceSelectionService.select(
                VoiceSelectionService.Backend.TTS,
                MODEL,
                AvailableAI.Flash3,
                ACHIRD_UUID,
                false);
    }

    private static String voiceName(List<GeminiTTS.RequestPayload.SpeakerVoiceConfig> voices, int index) {
        return voices.get(index).voice_config.prebuilt_voice_config.voice_name;
    }

    private static String errorBody(String message) {
        return "{\"error\":{\"code\":400,\"message\":\"" + message + "\",\"status\":\"INVALID_ARGUMENT\"}}";
    }

    private static UUID uuidForPreferred(String voice) {
        for (long value = 0; value < 100_000; value++) {
            UUID candidate = new UUID(0L, value);
            if (voice.equals(AvailableAI.Flash3.getRandomVoice(candidate, false))) return candidate;
        }
        throw new AssertionError("Could not find deterministic UUID for voice " + voice);
    }

}
