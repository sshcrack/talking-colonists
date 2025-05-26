package me.sshcrack.mc_talking.util;

import be.tarsos.dsp.*;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.resample.RateTransposer;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Helper class for audio processing operations like pitch shifting and resampling
 */
public class AudioHelper {
    /**
     * Changes the pitch of 16-bit PCM signed little-endian audio without changing its speed.
     *
     * @param inputPCMBytes The input audio as a byte array (16-bit PCM, little-endian)
     * @param sampleRate    The sample rate of the audio (e.g., 44100)
     * @param pitchFactor   Pitch multiplier (e.g., 1.25 = up pitch, 0.8 = down pitch)
     * @return The pitch-shifted audio as a byte array in the same format
     */
    public static byte[] changePitch(byte[] inputPCMBytes, int sampleRate, float pitchFactor) {
        if (pitchFactor == 1.0f) {
            // No pitch change needed, return original bytes
            return inputPCMBytes;
        }

        try {
            // Create audio input stream from PCM bytes
            UniversalAudioInputStream audioStream = getUniversalAudioInputStream(inputPCMBytes, sampleRate);

            // Create output stream to collect processed audio
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            var wsola = new WaveformSimilarityBasedOverlapAdd(WaveformSimilarityBasedOverlapAdd.Parameters.musicDefaults(pitchFactor, sampleRate));
            var rateTransposer = new RateTransposer(pitchFactor);
            AudioDispatcher dispatcher = new AudioDispatcher(audioStream, wsola.getInputBufferSize(), wsola.getOverlap());
            wsola.setDispatcher(dispatcher);
            dispatcher.addAudioProcessor(wsola);
            dispatcher.addAudioProcessor(rateTransposer);

            // Add final processor to collect the output
            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    try {
                        outputStream.write(audioEvent.getByteBuffer());
                        return true;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }

                @Override
                public void processingFinished() {
                    // no-op
                }
            });

            // Run the pipeline with exception handling
            dispatcher.run();

            // Return the processed audio bytes
            return outputStream.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return inputPCMBytes; // Return original on error
        }
    }

    private static @NotNull UniversalAudioInputStream getUniversalAudioInputStream(byte[] inputPCMBytes, int sampleRate) {
        // Create an audio format for 16-bit PCM mono audio
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(
                sampleRate, // Sample rate
                16, // Bit depth
                1, // Channels (mono)
                true, // Signed
                false // Little-endian
        );

        // Create an input stream from the byte array
        ByteArrayInputStream inputStream = new ByteArrayInputStream(inputPCMBytes);

        // Create and return a TarsosDSP audio input stream
        return new UniversalAudioInputStream(inputStream, format);
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

        double ratio = (double) inputSampleRate / outputSampleRate;
        int outputLength = (int) Math.ceil(input.length / ratio);

        short[] output = new short[outputLength];

        for (int i = 0; i < outputLength; i++) {
            double position = i * ratio;
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