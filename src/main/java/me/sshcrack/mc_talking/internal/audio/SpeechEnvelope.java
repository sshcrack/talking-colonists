package me.sshcrack.mc_talking.internal.audio;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client playback energy only; decoded voice samples are never retained. */
public final class SpeechEnvelope {
    private record Frame(float opening, long time) {}
    private static final ConcurrentHashMap<UUID, Frame> FRAMES = new ConcurrentHashMap<>();
    private static final long HOLD = 30_000_000L;
    private static final long RELEASE = 110_000_000L;

    private SpeechEnvelope() {}

    public static void accept(UUID entity, short[] pcm) {
        accept(entity, pcm, System.nanoTime());
    }

    static void accept(UUID entity, short[] pcm, long now) {
        if (entity == null) return;
        double sum = 0;
        for (short sample : pcm) sum += (double) sample * sample;
        float rms = pcm.length == 0 ? 0 : (float) Math.sqrt(sum / pcm.length) / 32768F;
        float target = Math.max(0, Math.min(1, (rms - 0.008F) / 0.15F));
        if (FRAMES.size() >= 256) FRAMES.entrySet().removeIf(e -> now - e.getValue().time > HOLD + RELEASE);
        if (FRAMES.size() >= 256 && !FRAMES.containsKey(entity)) return;
        FRAMES.compute(entity, (id, previous) -> new Frame(target == 0 ? 0 :
                previous == null ? target : Math.max(target, decay(previous, now) * 0.65F), now));
    }

    public static float opening(UUID entity) {
        return opening(entity, System.nanoTime());
    }

    static float opening(UUID entity, long now) {
        Frame frame = FRAMES.get(entity);
        return frame == null ? 0 : decay(frame, now);
    }

    private static float decay(Frame frame, long now) {
        return frame.opening * Math.max(0, 1F - Math.max(0, now - frame.time - HOLD) / (float) RELEASE);
    }

    public static void remove(UUID entity) { FRAMES.remove(entity); }
    public static void clear() { FRAMES.clear(); }
}
