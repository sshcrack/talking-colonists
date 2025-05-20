package me.sshcrack.mc_talking.manager;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class GeminiStream implements Supplier<short[]> {
    public static final int FRAME_SIZE_SAMPLES = 960;
    private static final int TARGET_SAMPLE_RATE = 48000;

    private final Queue<short[]> audioFrames = new ConcurrentLinkedQueue<>();
    private AudioChannel channel;
    @Nullable AudioPlayer player;
    private short[] remainingSamples = new short[0]; // For storing leftover samples

    public GeminiStream(AudioChannel channel) {
        this.channel = channel;
    }

    // Returns true if this was the first call to start the player
    public void addGeminiPcm(byte[] data, int sampleRate) {
        // Convert byte[] to short[] (assuming signed 16-bit PCM)
        short[] samples = new short[data.length / 2];
        for (int i = 0; i < samples.length; i++) {
            // Convert little-endian bytes to short
            samples[i] = (short) ((data[i * 2] & 0xFF) | (data[i * 2 + 1] << 8));
        }

        // Apply sample rate conversion if needed
        if (sampleRate != TARGET_SAMPLE_RATE) {
            samples = resampleAudio(samples, sampleRate, TARGET_SAMPLE_RATE);
        }

        // Combine with any remaining samples from previous calls
        if (remainingSamples.length > 0) {
            short[] combined = new short[remainingSamples.length + samples.length];
            System.arraycopy(remainingSamples, 0, combined, 0, remainingSamples.length);
            System.arraycopy(samples, 0, combined, remainingSamples.length, samples.length);
            samples = combined;
            remainingSamples = new short[0];
        }

        // Split into frames of FRAME_SIZE_SAMPLES
        int frameCount = samples.length / FRAME_SIZE_SAMPLES;
        int remainingCount = samples.length % FRAME_SIZE_SAMPLES;

        for (int i = 0; i < frameCount; i++) {
            short[] frame = new short[FRAME_SIZE_SAMPLES];
            System.arraycopy(samples, i * FRAME_SIZE_SAMPLES, frame, 0, FRAME_SIZE_SAMPLES);
            audioFrames.add(frame);
        }

        // Store any remaining samples for next time
        if (remainingCount > 0) {
            remainingSamples = new short[remainingCount];
            System.arraycopy(samples, frameCount * FRAME_SIZE_SAMPLES, remainingSamples, 0, remainingCount);
        }

        if (player == null || player.isStopped()) {
            if (player != null)
                player.stopPlaying();

            var encoder = vcApi.createEncoder(OpusEncoderMode.AUDIO);
            player = vcApi.createAudioPlayer(channel, encoder, this);

            if (!audioFrames.isEmpty())
                player.startPlaying();
        }
    }

    /**
     * Linear interpolation-based audio resampling
     *
     * @param input            Original audio samples
     * @param inputSampleRate  Original sample rate
     * @param outputSampleRate Target sample rate
     * @return Resampled audio data
     */
    private short[] resampleAudio(short[] input, int inputSampleRate, int outputSampleRate) {
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

    public void close() {

        if (player != null)
            player.stopPlaying();
    }

    @Override
    public short[] get() {
        // Return the next frame if available
        short[] frame = audioFrames.poll();

        if (frame != null) {
            return frame;
        }

        // Return null to stop playing when no more frames are available
        if (audioFrames.isEmpty()) {
            return null;
        }

        // In case we need to provide silence instead of stopping
        // (This branch shouldn't normally be reached)
        return new short[FRAME_SIZE_SAMPLES];
    }
}
