package me.sshcrack.mc_talking.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.AvailableAI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Bounded recovery for voices explicitly rejected by a Gemini audio backend.
 *
 * <p>Exclusions are scoped by backend, model and voice. They are intentionally process-memory only:
 * there is no on-disk recovery cache to become stale/corrupt across model changes. Entries expire
 * automatically and {@link #clear()} is called during server shutdown, so a transient provider
 * rejection never permanently changes a citizen's deterministic preferred voice.</p>
 */
public final class VoiceSelectionService {
    public enum Backend {
        LIVE("live"),
        TTS("tts");

        private final String diagnosticName;

        Backend(String diagnosticName) {
            this.diagnosticName = diagnosticName;
        }

        public String diagnosticName() {
            return diagnosticName;
        }
    }

    static final long REJECTION_TTL_MS = TimeUnit.HOURS.toMillis(6);
    private static final int MAX_REJECTIONS = 64;
    private static final Map<VoiceKey, Long> REJECTED_UNTIL = new ConcurrentHashMap<>();
    private static volatile LongSupplier clock = System::currentTimeMillis;

    private VoiceSelectionService() {
    }

    public static @NotNull String select(
            @NotNull Backend backend,
            @NotNull String model,
            @NotNull AvailableAI ai,
            @NotNull UUID citizenId,
            boolean female
    ) {
        purgeExpired();
        List<String> candidates = ai.getVoiceCandidates(female);
        String preferred = ai.getRandomVoice(citizenId, female);
        if (!isRejected(backend, model, preferred)) return preferred;

        int preferredIndex = candidates.indexOf(preferred);
        if (preferredIndex < 0) {
            throw new IllegalStateException("Preferred voice " + preferred + " is not present in its candidate list");
        }

        for (int offset = 1; offset < candidates.size(); offset++) {
            String candidate = candidates.get((preferredIndex + offset) % candidates.size());
            if (!isRejected(backend, model, candidate)) {
                McTalking.LOGGER.info(
                        "Voice recovery backend={} model={} preferred={} selected={} result=fallback",
                        backend.diagnosticName(), model, preferred, candidate);
                return candidate;
            }
        }

        throw new VoiceCandidatesExhaustedException(backend, model, preferred, candidates.size());
    }

    /** Exact/narrow Live recognition: a generic WebSocket 1007 is never sufficient evidence. */
    public static boolean isExplicitLiveVoiceRejection(int code, @Nullable String reason) {
        if (code != 1007 || reason == null) return false;
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("no matching speaker voice")
                || normalized.contains("speaker voice not found")
                || normalized.contains("unsupported speaker voice");
    }

    /**
     * Exact/narrow TTS recognition. The provider must return HTTP 400 with a structured error
     * message that both identifies the selected voice and describes a voice-specific rejection.
     */
    public static boolean isExplicitTtsVoiceRejection(
            int statusCode,
            @Nullable String responseBody,
            @Nullable String voice
    ) {
        if (statusCode != 400 || responseBody == null || voice == null || voice.isBlank()) return false;
        String message = structuredErrorMessage(responseBody);
        if (message == null) return false;

        String normalized = message.toLowerCase(Locale.ROOT);
        String normalizedVoice = voice.toLowerCase(Locale.ROOT);
        if (!normalized.contains(normalizedVoice)) return false;

        boolean voiceField = normalized.contains("voice")
                || normalized.contains("voice_name")
                || normalized.contains("voice name")
                || normalized.contains("voicename")
                || normalized.contains("prebuiltvoiceconfig");
        boolean rejectionReason = normalized.contains("no matching speaker voice")
                || normalized.contains("not found")
                || normalized.contains("unsupported")
                || normalized.contains("not supported")
                || normalized.contains("invalid");
        return voiceField && rejectionReason;
    }

    public static boolean noteLiveRejected(
            @NotNull AvailableAI ai,
            @Nullable String voice,
            int code,
            @Nullable String reason
    ) {
        if (!isExplicitLiveVoiceRejection(code, reason)) return false;
        return rememberRejection(Backend.LIVE, ai.getName(), ai, voice);
    }

    public static boolean noteTtsRejected(
            @NotNull AvailableAI ai,
            @NotNull String model,
            @Nullable String voice,
            int statusCode,
            @Nullable String responseBody
    ) {
        if (!isExplicitTtsVoiceRejection(statusCode, responseBody, voice)) return false;
        return rememberRejection(Backend.TTS, model, ai, voice);
    }

    public static void clear() {
        synchronized (REJECTED_UNTIL) {
            REJECTED_UNTIL.clear();
        }
    }

    private static boolean rememberRejection(
            Backend backend,
            String model,
            AvailableAI ai,
            @Nullable String voice
    ) {
        if (voice == null || voice.isBlank() || !isKnownVoice(ai, voice)) {
            McTalking.LOGGER.warn(
                    "Ignoring invalid voice recovery evidence backend={} model={} voice={}",
                    backend.diagnosticName(), model, voice);
            return false;
        }

        synchronized (REJECTED_UNTIL) {
            purgeExpiredLocked(now());
            VoiceKey key = new VoiceKey(backend, model, voice);
            if (!REJECTED_UNTIL.containsKey(key) && REJECTED_UNTIL.size() >= MAX_REJECTIONS) {
                REJECTED_UNTIL.entrySet().stream()
                        .min(Map.Entry.comparingByValue())
                        .ifPresent(entry -> REJECTED_UNTIL.remove(entry.getKey(), entry.getValue()));
            }
            REJECTED_UNTIL.put(key, now() + REJECTION_TTL_MS);
        }

        McTalking.LOGGER.warn(
                "Voice recovery backend={} model={} voice={} result=excluded ttlMs={}",
                backend.diagnosticName(), model, voice, REJECTION_TTL_MS);
        return true;
    }

    private static boolean isKnownVoice(AvailableAI ai, String voice) {
        return ai.getVoiceCandidates(false).contains(voice) || ai.getVoiceCandidates(true).contains(voice);
    }

    private static boolean isRejected(Backend backend, String model, String voice) {
        Long until = REJECTED_UNTIL.get(new VoiceKey(backend, model, voice));
        return until != null && until > now();
    }

    private static void purgeExpired() {
        synchronized (REJECTED_UNTIL) {
            purgeExpiredLocked(now());
        }
    }

    private static void purgeExpiredLocked(long now) {
        REJECTED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    @Nullable
    private static String structuredErrorMessage(String responseBody) {
        try {
            var parsed = JsonParser.parseString(responseBody);
            if (!parsed.isJsonObject()) return null;
            JsonObject root = parsed.getAsJsonObject();
            if (!root.has("error") || !root.get("error").isJsonObject()) return null;
            JsonObject error = root.getAsJsonObject("error");
            if (!error.has("message") || !error.get("message").isJsonPrimitive()) return null;
            return error.get("message").getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long now() {
        return clock.getAsLong();
    }

    static void setClockForTests(LongSupplier testClock) {
        clock = testClock;
    }

    static void resetClockForTests() {
        clock = System::currentTimeMillis;
    }

    static int exclusionCountForTests() {
        purgeExpired();
        return REJECTED_UNTIL.size();
    }

    private record VoiceKey(Backend backend, String model, String voice) {
    }

    public static final class VoiceCandidatesExhaustedException extends IllegalStateException {
        private final Backend backend;
        private final String model;
        private final String preferredVoice;
        private final int candidateCount;

        private VoiceCandidatesExhaustedException(Backend backend, String model, String preferredVoice, int candidateCount) {
            super("Voice recovery exhausted backend=" + backend.diagnosticName()
                    + " model=" + model
                    + " preferred=" + preferredVoice
                    + " candidates=" + candidateCount);
            this.backend = backend;
            this.model = model;
            this.preferredVoice = preferredVoice;
            this.candidateCount = candidateCount;
        }

        public Backend backend() {
            return backend;
        }

        public String model() {
            return model;
        }

        public String preferredVoice() {
            return preferredVoice;
        }

        public int candidateCount() {
            return candidateCount;
        }
    }
}
