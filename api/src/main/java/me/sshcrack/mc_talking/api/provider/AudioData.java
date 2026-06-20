package me.sshcrack.mc_talking.api.provider;

import java.util.Objects;

public final class AudioData {
    private final byte[] pcm;
    private final int sampleRate;
    private final int channels;

    public AudioData(byte[] pcm, int sampleRate, int channels) {
        this.pcm = Objects.requireNonNull(pcm);
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    public byte[] pcm() {
        return pcm;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioData audioData)) return false;
        return sampleRate == audioData.sampleRate
                && channels == audioData.channels
                && Objects.equals(pcm, audioData.pcm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pcm, sampleRate, channels);
    }

    @Override
    public String toString() {
        return "AudioData[" + pcm.length + " bytes, "
                + sampleRate + "Hz, "
                + channels + "ch]";
    }
}
