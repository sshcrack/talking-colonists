package me.sshcrack.mc_talking.api.provider;

import java.util.Objects;

public final class ProviderSelection {
    public static final String DISABLED = "disabled";

    private final String sttProviderId;
    private final String llmProviderId;
    private final String ttsProviderId;
    private final String liveBundleProviderId;

    public ProviderSelection(
            String sttProviderId,
            String llmProviderId,
            String ttsProviderId,
            String liveBundleProviderId
    ) {
        this.sttProviderId = Objects.requireNonNullElse(sttProviderId, DISABLED);
        this.llmProviderId = Objects.requireNonNullElse(llmProviderId, DISABLED);
        this.ttsProviderId = Objects.requireNonNullElse(ttsProviderId, DISABLED);
        this.liveBundleProviderId = Objects.requireNonNullElse(liveBundleProviderId, DISABLED);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String sttProviderId() {
        return sttProviderId;
    }

    public String llmProviderId() {
        return llmProviderId;
    }

    public String ttsProviderId() {
        return ttsProviderId;
    }

    public String liveBundleProviderId() {
        return liveBundleProviderId;
    }

    public boolean hasLiveBundle() {
        return !DISABLED.equals(liveBundleProviderId);
    }

    public boolean hasComposable() {
        return !DISABLED.equals(sttProviderId)
                && !DISABLED.equals(llmProviderId)
                && !DISABLED.equals(ttsProviderId);
    }

    /**
     * Validate this selection against the registry. Returns a new selection
     * with unknown provider IDs replaced by the default (first registered for that capability),
     * or DISABLED if none are registered.
     */
    public ProviderSelection validate(AiProviderRegistry registry) {
        Builder builder = builder();

        if (hasLiveBundle()) {
            if (registry.getProvider(liveBundleProviderId, Capability.LIVE_BUNDLE).isPresent()) {
                builder.liveBundleProviderId(liveBundleProviderId);
            } else {
                builder.liveBundleProviderId(
                        registry.firstForCapability(Capability.LIVE_BUNDLE)
                                .map(AiProvider::providerId)
                                .orElse(DISABLED));
            }
            return builder.build();
        }

        if (hasComposable()) {
            builder.sttProviderId(resolveProvider(sttProviderId, Capability.STT, registry))
                    .llmProviderId(resolveProvider(llmProviderId, Capability.LLM, registry))
                    .ttsProviderId(resolveProvider(ttsProviderId, Capability.TTS, registry));
        }

        return builder.build();
    }

    private static String resolveProvider(String configuredId, Capability capability, AiProviderRegistry registry) {
        if (DISABLED.equals(configuredId)) return DISABLED;
        if (registry.getProvider(configuredId, capability).isPresent()) return configuredId;
        return registry.firstForCapability(capability)
                .map(AiProvider::providerId)
                .orElse(DISABLED);
    }

    public static final class Builder {
        private String sttProviderId = DISABLED;
        private String llmProviderId = DISABLED;
        private String ttsProviderId = DISABLED;
        private String liveBundleProviderId = DISABLED;

        public Builder sttProviderId(String sttProviderId) {
            this.sttProviderId = sttProviderId;
            return this;
        }

        public Builder llmProviderId(String llmProviderId) {
            this.llmProviderId = llmProviderId;
            return this;
        }

        public Builder ttsProviderId(String ttsProviderId) {
            this.ttsProviderId = ttsProviderId;
            return this;
        }

        public Builder liveBundleProviderId(String liveBundleProviderId) {
            this.liveBundleProviderId = liveBundleProviderId;
            return this;
        }

        public ProviderSelection build() {
            return new ProviderSelection(sttProviderId, llmProviderId, ttsProviderId, liveBundleProviderId);
        }
    }
}
