package me.sshcrack.mc_talking.api.provider;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SttProvider extends AiProvider {
    CompletableFuture<String> transcribe(AudioData audio);

    @Override
    default Collection<Capability> capabilities() {
        return List.of(Capability.STT);
    }
}
