package me.sshcrack.mc_talking.api.provider;

import java.util.Objects;

public final class ProviderHealth {
    private final String providerId;
    private final boolean available;
    private final String lastError;

    public ProviderHealth(String providerId, boolean available, String lastError) {
        this.providerId = Objects.requireNonNull(providerId);
        this.available = available;
        this.lastError = lastError;
    }

    public static ProviderHealth available(String providerId) {
        return new ProviderHealth(providerId, true, null);
    }

    public static ProviderHealth unavailable(String providerId, String lastError) {
        return new ProviderHealth(providerId, false,
                Objects.requireNonNullElse(lastError, "Unknown error"));
    }

    public String providerId() {
        return providerId;
    }

    public boolean available() {
        return available;
    }

    public String lastError() {
        return lastError;
    }
}
