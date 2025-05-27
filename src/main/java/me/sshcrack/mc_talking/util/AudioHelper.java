package me.sshcrack.mc_talking.util;

/**
 * Helper class for audio processing operations like pitch shifting and resampling
 */
public class AudioHelper {    
    /**
     * Changes the pitch of audio samples without changing its speed.
     *
     * @param inputSamples The input audio as a short array
     * @param sampleRate   The sample rate of the audio (e.g., 44100)
     * @param pitchFactor  Pitch multiplier (e.g., 1.25 = up pitch, 0.8 = down pitch)
     * @return The pitch-shifted audio as a short array
     */    
    public static short[] changePitch(short[] inputSamples, int sampleRate, float pitchFactor) {
        if (pitchFactor == 1.0f) {
            // No pitch change needed, return original samples
            return inputSamples;
        }

        /*
        try {
            // Two-step approach to change pitch without changing duration:
            
            // Step 1: Resample to change pitch (this also changes duration)
            // When pitchFactor > 1, this increases pitch and shortens duration
            // When pitchFactor < 1, this decreases pitch and lengthens duration
            int newSampleRate = (int)(sampleRate * pitchFactor);
            short[] resampledAudio = resampleAudio(inputSamples, sampleRate, newSampleRate);
            
            // Step 2: Time-stretch to restore original duration without affecting pitch
            // When pitchFactor > 1, we need to stretch the audio by pitchFactor
            // When pitchFactor < 1, we need to compress the audio by pitchFactor
            short[] timeStretchedAudio = timeStretch(resampledAudio, 1.0f / pitchFactor);
            
            return timeStretchedAudio;
        } catch (Exception e) {
            e.printStackTrace();
            return inputSamples; // Return original on error
        }
        */
        //TODO this is sadly still not working, so we just return the original samples
        return inputSamples;
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