package me.sshcrack.mc_talking.internal.audio;

/**
 * Conservative local speech-candidate detector for decoded Simple Voice Chat PCM.
 *
 * <p>This is deliberately not a replacement for Gemini's server-side VAD. It only decides whether
 * a real microphone packet is strong and sustained enough to justify local playback barge-in.
 * Provider turn detection still receives the decoded microphone stream and remains authoritative.</p>
 */
public final class PcmSpeechDetector {
    private static final int ACTIVE_SAMPLE_THRESHOLD = 220;
    private static final int MIN_PEAK = 700;
    private static final double MIN_RMS = 180.0;
    private static final double MIN_ACTIVE_RATIO = 0.03;

    private PcmSpeechDetector() {
    }

    public static boolean isSpeechCandidate(short[] samples) {
        if (samples == null || samples.length == 0) return false;

        long sumSquares = 0L;
        int peak = 0;
        int activeSamples = 0;
        for (short sample : samples) {
            int absolute = Math.abs((int) sample);
            peak = Math.max(peak, absolute);
            if (absolute >= ACTIVE_SAMPLE_THRESHOLD) activeSamples++;
            sumSquares += (long) sample * sample;
        }

        double rms = Math.sqrt((double) sumSquares / samples.length);
        double activeRatio = (double) activeSamples / samples.length;
        return peak >= MIN_PEAK && rms >= MIN_RMS && activeRatio >= MIN_ACTIVE_RATIO;
    }
}
