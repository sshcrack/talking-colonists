package me.sshcrack.mc_talking.util;

/**
 * Helper class for audio processing operations like pitch shifting and resampling
 */
public class AudioHelper {
    /**
     * Changes the pitch of audio samples without changing its speed.
     * Uses the Sonic DSP library for high-fidelity speech processing.
     *
     * @param inputSamples The input audio as a short array
     * @param sampleRate   The sample rate of the audio (e.g., 44100)
     * @param pitchFactor  Pitch multiplier (e.g., 1.25 = up pitch, 0.8 = down pitch)
     * @return The pitch-shifted audio as a short array
     */
    public static short[] changePitch(short[] inputSamples, int sampleRate, float pitchFactor) {
        if (pitchFactor == 1.0f || inputSamples == null || inputSamples.length == 0) {
            // No pitch change needed, return original samples
            return inputSamples;
        }

        // Initialize Sonic for mono audio (1 channel)
        Sonic sonic = new Sonic(sampleRate, 1);

        // Sonic handles pitch, speed, and rate independently.
        // We only alter the pitch factor here.
        sonic.setPitch(pitchFactor);

        // 1. Feed the raw audio into Sonic's internal processing stream
        sonic.writeShortToStream(inputSamples, inputSamples.length);

        // 2. Tell Sonic we are done writing so it can process the final "tail" of the audio
        sonic.flushStream();

        // 3. Retrieve the processed audio
        // Note: The output array length might slightly differ from the input length
        // due to the nature of Pitch Synchronous Overlap-Add algorithms.
        int samplesAvailable = sonic.samplesAvailable();
        short[] outputSamples = new short[samplesAvailable];

        sonic.readShortFromStream(outputSamples, samplesAvailable);

        return outputSamples;
    }

    /**
     * Time-stretches audio without changing pitch
     *
     * @param input         Original audio samples
     * @param stretchFactor Factor by which to stretch (>1 = longer, <1 = shorter)
     * @return Time-stretched audio data
     */
    private static short[] timeStretch(short[] input, float stretchFactor) {
        int outputLength = (int) (input.length * stretchFactor);
        short[] output = new short[outputLength];

        for (int i = 0; i < outputLength; i++) {
            float sourcePos = i / stretchFactor;
            int index = (int) sourcePos;
            float fraction = sourcePos - index;

            if (index >= input.length - 1) {
                output[i] = input[input.length - 1];
            } else {
                // Linear interpolation
                short sample1 = input[index];
                short sample2 = input[index + 1];
                output[i] = (short) (sample1 + fraction * (sample2 - sample1));
            }
        }

        return output;
    }

    /**
     * Linear interpolation-based audio resampling for changing sample rate
     *
     * @param input            Original audio samples
     * @param inputSampleRate  Original sample rate
     * @param outputSampleRate Target sample rate
     * @return Resampled audio data
     */
    public static short[] resampleAudio(short[] input, int inputSampleRate, int outputSampleRate) {
        if (inputSampleRate == outputSampleRate) {
            return input;
        }

        double ratio = (double) outputSampleRate / inputSampleRate;
        int outputLength = (int) Math.ceil(input.length * ratio);

        short[] output = new short[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double position = i / ratio;
            int index = (int) position;
            double fraction = position - index;

            if (index >= input.length - 1) {
                output[i] = input[input.length - 1];
            } else {
                // Linear interpolation
                short sample1 = input[index];
                short sample2 = input[index + 1];
                output[i] = (short) (sample1 + fraction * (sample2 - sample1));
            }
        }

        return output;
    }
}
