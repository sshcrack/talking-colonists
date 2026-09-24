package me.sshcrack.mc_talking.internal.audio;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side: while the local player talks to a citizen, every other citizen's voice plays quieter.
 * Only this player's playback changes. The gain ramps across each audio frame, so voices fade
 * instead of clicking. Citizens are recognised once they have been rendered.
 */
public final class VoiceDucking {
    /** Gain change per 20 ms frame: from full to 30 % in about 60 ms, and back. */
    static final float STEP = 0.25F;

    private static final Map<UUID, Boolean> CITIZENS = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> GAINS = new ConcurrentHashMap<>();

    private VoiceDucking() {
    }

    public static void markCitizen(UUID entity) {
        if (CITIZENS.size() >= 4096) CITIZENS.clear();
        CITIZENS.put(entity, Boolean.TRUE);
    }

    public static void clear() {
        CITIZENS.clear();
        GAINS.clear();
    }

    /**
     * Scales one received frame in place. {@code partner} is the citizen the local player is
     * talking to, or null. {@code citizenSource} says whether the sound comes from a citizen at all;
     * other players are never ducked. {@code duckedGain} of 1 turns ducking off.
     */
    public static short[] apply(UUID source, boolean citizenSource, @Nullable UUID partner, float duckedGain,
                                short[] pcm) {
        boolean duck = partner != null && citizenSource && !source.equals(partner) && duckedGain < 1F;
        float target = duck ? Math.max(0F, duckedGain) : 1F;
        float current = GAINS.getOrDefault(source, 1F);
        float next = approach(current, target);
        if (next >= 1F) GAINS.remove(source);
        else GAINS.put(source, next);
        if (current >= 1F && next >= 1F) return pcm;
        return scale(pcm, current, next);
    }

    public static boolean isCitizen(UUID entity) {
        return CITIZENS.containsKey(entity);
    }

    static float approach(float current, float target) {
        if (current < target) return Math.min(target, current + STEP);
        return Math.max(target, current - STEP);
    }

    /** Linear gain ramp from {@code from} to {@code to} across the frame. */
    static short[] scale(short[] pcm, float from, float to) {
        int n = pcm.length;
        for (int i = 0; i < n; i++) {
            float gain = n == 1 ? to : from + (to - from) * i / (n - 1);
            pcm[i] = (short) Math.round(pcm[i] * gain);
        }
        return pcm;
    }
}
