package me.sshcrack.mc_talking.api.audio;

public record AudioFormat(int sampleRate, int sampleSizeInBits, boolean signed, boolean bigEndian) {
    public static final AudioFormat DEFAULT_PCM = new AudioFormat(16000, 16, true, false);
}
