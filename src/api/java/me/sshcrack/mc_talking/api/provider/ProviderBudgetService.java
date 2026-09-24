package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only provider capacity, quota and configuration (API 2.1,
 * {@link me.sshcrack.mc_talking.api.ApiFeature#PROVIDER_BUDGET}). Use it to schedule expensive work
 * when capacity is free and to show honest "citizens are tired" messages when quota is exhausted.
 */
public final class ProviderBudgetService {
    private ProviderBudgetService() {
    }

    public static @NotNull ProviderBudgetView snapshot() {
        return TalkingColonistsApi.services().providerStatus().snapshot();
    }

    public static @NotNull ProviderConfigView config() {
        return TalkingColonistsApi.services().providerStatus().config();
    }

    /** Listeners run in ascending {@code order}, then by ID. */
    public static @NotNull AddonRegistration registerQuotaListener(@NotNull String id, int order,
                                                                  @NotNull ProviderQuotaListener listener) {
        return TalkingColonistsApi.services().providerStatus().registerQuotaListener(id, order, listener);
    }

    /** Reloads the Talking Colonists config file from disk, for example after an addon edited it. */
    public static void reloadConfig() {
        TalkingColonistsApi.services().providerStatus().reloadConfig();
    }
}
