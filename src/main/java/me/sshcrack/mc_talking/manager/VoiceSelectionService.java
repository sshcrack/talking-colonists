package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.McTalking;
import me.sshcrack.mc_talking.config.AvailableAI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Bounded in-memory recovery for voices explicitly rejected by Gemini Live.
 * Exclusions are scoped by model and expire automatically, so one transient/model-specific
 * rejection cannot permanently change every citizen's deterministic preferred voice.
 */
public final class VoiceSelectionService {
    private static final long REJECTION_TTL_MS = TimeUnit.HOURS.toMillis(6);
    private static final int MAX_REJECTIONS = 64;
    private static final Map<String, Long> REJECTED_UNTIL = new ConcurrentHashMap<>();

    private VoiceSelectionService() {
    }

    public static @NotNull String select(@NotNull AvailableAI ai, @NotNull UUID citizenId, boolean female) {
        purgeExpired();
        List<String> candidates = ai.getVoiceCandidates(female);
        String preferred = ai.getRandomVoice(citizenId, female);
        if (!isRejected(ai, preferred)) return preferred;

        int preferredIndex = Math.max(0, candidates.indexOf(preferred));
        for (int offset = 1; offset <= candidates.size(); offset++) {
            String candidate = candidates.get((preferredIndex + offset) % candidates.size());
            if (!isRejected(ai, candidate)) {
                McTalking.LOGGER.info("Voice {} is temporarily excluded for {}; using stable fallback {}",
                        preferred, ai.getName(), candidate);
                return candidate;
            }
        }
        return preferred;
    }

    /** Exact/narrow recognition: generic websocket 1007 errors do not poison the voice list. */
    public static boolean isExplicitVoiceRejection(int code, @Nullable String reason) {
        if (code != 1007 || reason == null) return false;
        String normalized = reason.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("no matching speaker voice")
                || normalized.contains("speaker voice not found")
                || normalized.contains("unsupported speaker voice");
    }

    public static void noteRejected(@NotNull AvailableAI ai, @Nullable String voice, int code, @Nullable String reason) {
        if (voice == null || !isExplicitVoiceRejection(code, reason)) return;
        purgeExpired();
        if (REJECTED_UNTIL.size() >= MAX_REJECTIONS) {
            REJECTED_UNTIL.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(entry -> REJECTED_UNTIL.remove(entry.getKey(), entry.getValue()));
        }
        REJECTED_UNTIL.put(key(ai, voice), System.currentTimeMillis() + REJECTION_TTL_MS);
        McTalking.LOGGER.warn("Gemini rejected voice {} for model {}; temporarily excluding it", voice, ai.getName());
    }

    public static void clear() {
        REJECTED_UNTIL.clear();
    }

    private static boolean isRejected(AvailableAI ai, String voice) {
        Long until = REJECTED_UNTIL.get(key(ai, voice));
        return until != null && until > System.currentTimeMillis();
    }

    private static String key(AvailableAI ai, String voice) {
        return ai.getName() + "\u0000" + voice;
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        REJECTED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
