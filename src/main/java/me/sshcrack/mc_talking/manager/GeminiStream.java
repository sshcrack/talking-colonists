package me.sshcrack.mc_talking.manager;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import me.sshcrack.mc_talking.util.AudioHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.TARGET_SAMPLE_RATE;
import static me.sshcrack.mc_talking.McTalkingVoicechatPlugin.vcApi;

public class GeminiStream implements Supplier<short[]> {
    public static final int FRAME_SIZE_SAMPLES = 960;
    // Minimum buffer size for effective pitch shifting (about 100ms of audio)
    private static final int MIN_BUFFER_SIZE_FOR_PITCH = TARGET_SAMPLE_RATE * 2;
    // Minimum number of frames to buffer before starting playback (about 500ms)
    private static final int MIN_FRAMES_BEFORE_PLAYBACK = 100;

    private final Queue<short[]> audioFrames = new ConcurrentLinkedQueue<>();
    private final AudioChannel channel;
    @Nullable AudioPlayer player;
    private short[] remainingSamples = new short[0]; // For storing leftover samples
    private float pitchFactor = 1.0f;
    private boolean isPreBuffering = true;

    private int lastSampleRate = TARGET_SAMPLE_RATE; // Last processed sample rate

    // Single buffer for incoming audio data
    private final List<byte[]> incomingData = Collections.synchronizedList(new ArrayList<>());
    private int totalBufferedBytes = 0;

    private Runnable onPause;
    private OpusEncoder encoder;

    public GeminiStream(AudioChannel channel) {
        this.channel = channel;
    }

    public void setOnPause(Runnable onPause) {
        this.onPause = onPause;
    }

    public void flushAudio() {
        // Process any remaining buffered data
        if (!incomingData.isEmpty()) {
            processBufferedData(lastSampleRate, true);
        }
    }

    /**
     * Add audio data with pitch shifting applied
     *
     * @param data       Raw PCM audio data as byte array
     * @param sampleRate Sample rate of the audio data
     */
    public boolean addGeminiPcmWithPitch(byte[] data, int sampleRate) {
        lastSampleRate = sampleRate;

        // Add the new data to our buffer
        if (data.length > 0) {
            synchronized (incomingData) {
                incomingData.add(data);
                totalBufferedBytes += data.length;
            }
        }

        // Only process if we have enough data for effective pitch shifting.
        // We collect more data before processing to ensure smoother playback.
        int bufferedBytes = totalBufferedBytes;
        if (bufferedBytes >= MIN_BUFFER_SIZE_FOR_PITCH * 2) {
            return processBufferedData(sampleRate, false);
        }

        return false;
    }

    /**
     * Process the buffered data, applying pitch shifting and preparing for playback
     *
     * @param sampleRate Sample rate of the audio data
     */
    private boolean processBufferedData(int sampleRate, boolean flushed) {
        byte[] combined;

        synchronized (incomingData) {
            if (incomingData.isEmpty()) {
                return false;
            }

            // Combine all buffered chunks into a single array
            int totalBytes = totalBufferedBytes;
            combined = new byte[totalBytes];
            int offset = 0;
            for (byte[] chunk : incomingData) {
                System.arraycopy(chunk, 0, combined, offset, chunk.length);
                offset += chunk.length;
            }

            incomingData.clear();
            totalBufferedBytes = 0;
        }

        short[] samples = vcApi.getAudioConverter().bytesToShorts(combined);

        samples = AudioHelper.changePitch(samples, sampleRate, pitchFactor);
        // Apply sample rate conversion if needed
        if (sampleRate != TARGET_SAMPLE_RATE) {
            samples = AudioHelper.resampleAudio(samples, sampleRate, TARGET_SAMPLE_RATE);
        }

        // Process the audio samples
        return processAudioSamples(samples, flushed);
    }

    /**
     * Process audio samples and prepare them for playback
     *
     * @param samples Audio samples to process
     */
    private boolean processAudioSamples(short[] samples, boolean flushed) {
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

        // Initialize or restart the player when needed
        if (player == null || player.isStopped()) {
            if (player != null)
                player.stopPlaying();

            // Only start playing when we have enough buffered frames
            if (!audioFrames.isEmpty() && (!isPreBuffering || audioFrames.size() >= MIN_FRAMES_BEFORE_PLAYBACK || flushed)) {
                encoder = vcApi.createEncoder(OpusEncoderMode.AUDIO);
                player = vcApi.createAudioPlayer(channel, encoder, this);


                isPreBuffering = false;
                player.startPlaying();
                return true;
            }
        }

        return false;
    }

    public void stop() {
        audioFrames.clear();
        remainingSamples = new short[0];
        isPreBuffering = true;
        if (player != null) {
            player.stopPlaying();
            long deadline = System.currentTimeMillis() + 2000;
            try {
                while (!player.isStopped() && System.currentTimeMillis() < deadline) {
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            player = null;
        }
        if (encoder != null) {
            try { encoder.close(); } catch (Exception e) { /* swallow */ }
            encoder = null;
        }
    }

    public void close() {
        stop();
    }

    @Override
    public short[] get() {
        short[] frame = audioFrames.poll();
        if (frame != null) {
            return frame;
        }

        // Queue is empty — pause playback and notify the client
        isPreBuffering = true;
        if (onPause != null) {
            onPause.run();
        }
        return null;
    }

    public void setPitch(float pitch) {
        this.pitchFactor = pitch;
    }
}
