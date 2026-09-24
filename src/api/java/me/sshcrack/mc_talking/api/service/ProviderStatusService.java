package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.provider.ProviderBudgetView;
import me.sshcrack.mc_talking.api.provider.ProviderConfigView;
import me.sshcrack.mc_talking.api.provider.ProviderQuotaListener;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime service behind {@link me.sshcrack.mc_talking.api.provider.ProviderBudgetService}. */
public interface ProviderStatusService {
    @NotNull ProviderBudgetView snapshot();

    @NotNull ProviderConfigView config();

    @NotNull AddonRegistration registerQuotaListener(@NotNull String id, int order, @NotNull ProviderQuotaListener listener);

    void reloadConfig();
}
