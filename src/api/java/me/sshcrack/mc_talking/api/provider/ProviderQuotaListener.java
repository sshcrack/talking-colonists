package me.sshcrack.mc_talking.api.provider;

import org.jetbrains.annotations.NotNull;

/**
 * Notified on the server thread when a model's quota state changes, including when a backoff
 * expires. Checked about once a second, so a very short exhaustion can be missed.
 */
@FunctionalInterface
public interface ProviderQuotaListener {
    void onQuotaChanged(@NotNull ModelQuotaView previous, @NotNull ModelQuotaView current);
}
