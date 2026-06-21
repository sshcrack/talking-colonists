package me.sshcrack.mc_talking.api.provider;

import java.util.concurrent.CompletableFuture;

public interface SttProvider extends AiProvider {
    CompletableFuture<String> transcribe(short[] audio, int sampleRate);
}
