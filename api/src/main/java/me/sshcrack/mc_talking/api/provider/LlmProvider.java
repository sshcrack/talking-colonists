package me.sshcrack.mc_talking.api.provider;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface LlmProvider extends AiProvider {
    CompletableFuture<LlmResponse> generate(LlmRequest request);

    @Override
    default Collection<Capability> capabilities() {
        return List.of(Capability.LLM);
    }
}
