package me.sshcrack.mc_talking.util;

import me.sshcrack.mc_talking.api.provider.*;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LlmFallback {
    private static final Logger LOGGER = LoggerFactory.getLogger("LlmFallback");
    private static final long TIMEOUT_SECONDS = 30;

    private LlmFallback() {
    }

    public static Optional<String> callLlm(String systemPrompt, String userText) {
        return callLlm(systemPrompt, userText, McTalkingConfig.buildProviderSelection());
    }

    public static Optional<String> callLlm(String systemPrompt, String userText, ProviderSelection selection) {
        if (systemPrompt == null || userText == null) return Optional.empty();

        var request = LlmRequest.builder()
                .systemPrompt(systemPrompt)
                .userText(userText)
                .build();

        // 1. Try configured LLM provider
        if (selection.hasComposable() && !ProviderSelection.DISABLED.equals(selection.llmProviderId())) {
            var opt = AiProviderRegistry.getProvider(selection.llmProviderId(), LlmProvider.class);
            if (opt.isPresent()) {
                try {
                    String result = callLlmProvider(opt.get(), request);
                    if (result != null) {
                        LOGGER.debug("Used configured LLM provider '{}'", selection.llmProviderId());
                        return Optional.of(result);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Configured LLM provider '{}' failed, trying fallbacks", selection.llmProviderId(), e);
                }
            }
        }

        // 2. Fallback: try any other registered LLM provider
        var providers = AiProviderRegistry.getProviders(Capability.LLM);
        for (var provider : providers) {
            if (selection.hasComposable() && provider.providerId().equals(selection.llmProviderId())) {
                continue;
            }
            if (!(provider instanceof LlmProvider llmProvider)) continue;
            try {
                String result = callLlmProvider(llmProvider, request);
                if (result != null) {
                    LOGGER.info("Fell back to LLM provider '{}'", provider.providerId());
                    return Optional.of(result);
                }
            } catch (Exception e) {
                LOGGER.warn("Fallback LLM provider '{}' failed", provider.providerId(), e);
            }
        }

        LOGGER.warn("No LLM provider available (neither configured nor fallback)");
        return Optional.empty();
    }

    private static String callLlmProvider(LlmProvider provider, LlmRequest request)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<LlmResponse> future = provider.generate(request);
        LlmResponse response = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (response != null && !response.text().isBlank()) {
            return response.text();
        }
        return null;
    }
}
