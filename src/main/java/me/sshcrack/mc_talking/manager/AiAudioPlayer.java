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

public class AiAudioPlayer implements Supplier<short[]> {
    public static final int FRAME_SIZE_SAMPLES = 960;
    private static final int MIN_BUFFER_SIZE_FOR_PITCH = TARGET_SAMPLE_RATE * 2;
    private static final int MIN_FRAMES_BEFORE_PLAYBACK = 100;

    private final Queue<short[]> audioFrames = new ConcurrentLinkedQueue<>();
    private final AudioChannel channel;
    @Nullable
    AudioPlayer player;
    private short[] remainingSamples = new short[0];
    private float pitchFactor = 1.0f;
    private boolean isPreBuffering = true;
    private int lastSampleRate = TARGET_SAMPLE_RATE;
    private final List<byte[]> incomingData = Collections.synchronizedList(new ArrayList<>());
    private int totalBufferedBytes = 0;
    private Runnable onPause;
    private OpusEncoder encoder;

    public AiAudioPlayer(AudioChannel channel) {
        this.channel = channel;
    }

    public void setOnPause(Runnable onPause) {
        this.onPause = onPause;
    }

    public void flushAudio() {
        if (!incomingData.isEmpty()) processBufferedData(lastSampleRate, true);
    }

    public boolean addGeminiPcmWithPitch(byte[] data, int sampleRate) {
        lastSampleRate = sampleRate;
        if (data.length > 0) {
            synchronized (incomingData) {
                incomingData.add(data);
                totalBufferedBytes += data.length;
            }
        }
        int bufferedBytes = totalBufferedBytes;
        if (bufferedBytes >= MIN_BUFFER_SIZE_FOR_PITCH * 2) {
            return processBufferedData(sampleRate, false);
        }
        return false;
    }

    private boolean processBufferedData(int sampleRate, boolean flushed) {
        byte[] combined;
        synchronized (incomingData) {
            if (incomingData.isEmpty()) return false;
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
        if (sampleRate != TARGET_SAMPLE_RATE) {
            samples = AudioHelper.resampleAudio(samples, sampleRate, TARGET_SAMPLE_RATE);
        }
        return processAudioSamples(samples, flushed);
    }

    private boolean processAudioSamples(short[] samples, boolean flushed) {
        if (remainingSamples.length > 0) {
            short[] combined = new short[remainingSamples.length + samples.length];
            System.arraycopy(remainingSamples, 0, combined, 0, remainingSamples.length);
            System.arraycopy(samples, 0, combined, remainingSamples.length, samples.length);
            samples = combined;
            remainingSamples = new short[0];
        }
        int frameCount = samples.length / FRAME_SIZE_SAMPLES;
        int remainingCount = samples.length % FRAME_SIZE_SAMPLES;
        for (int i = 0; i < frameCount; i++) {
            short[] frame = new short[FRAME_SIZE_SAMPLES];
            System.arraycopy(samples, i * FRAME_SIZE_SAMPLES, frame, 0, FRAME_SIZE_SAMPLES);
            audioFrames.add(frame);
        }
        if (remainingCount > 0) {
            remainingSamples = new short[remainingCount];
            System.arraycopy(samples, frameCount * FRAME_SIZE_SAMPLES, remainingSamples, 0, remainingCount);
        }
        if (player == null || player.isStopped()) {
            if (player != null) player.stopPlaying();
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
            try {
                encoder.close();
            } catch (Exception ignored) {
            }
            encoder = null;
        }
    }

    public void close() {
        stop();
    }

    @Override
    @Nullable
    public short[] get() {
        short[] frame = audioFrames.poll();
        if (frame != null) return frame;
        isPreBuffering = true;
        if (onPause != null) onPause.run();
        return null;
    }

    public void setPitch(float pitch) {
        this.pitchFactor = pitch;
    }
}
