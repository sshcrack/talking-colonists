package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.ConversationManager;
import me.sshcrack.mc_talking.api.provider.ProviderBudgetView;
import me.sshcrack.mc_talking.api.provider.ProviderConfigView;
import me.sshcrack.mc_talking.api.provider.ProviderQuotaListener;
import me.sshcrack.mc_talking.api.provider.SlotUsage;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.ProviderStatusService;
import me.sshcrack.mc_talking.config.McTalkingConfig;
import me.sshcrack.mc_talking.config.QuotaSnapshot;
import me.sshcrack.mc_talking.config.QuotaTracker;
import me.sshcrack.mc_talking.config.TtsQuotaManager;
import me.sshcrack.mc_talking.internal.provider.ProviderBudgetRuntime;
import org.jetbrains.annotations.NotNull;

/** Live wiring of {@link ProviderBudgetRuntime} to the session registries, quota trackers and config. */
public final class ProviderStatusServiceBackend implements ProviderStatusService {
    public static final ProviderBudgetRuntime RUNTIME = new ProviderBudgetRuntime(new ProviderBudgetRuntime.Sources() {
        @Override
        public @NotNull SlotUsage foreground() {
            return new SlotUsage(ConversationManager.getUsedForegroundSlots(),
                    McTalkingConfig.INSTANCE.instance().maxConcurrentAgents);
        }

        @Override
        public @NotNull SlotUsage background() {
            return new SlotUsage(ConversationManager.getUsedBackgroundSlots(), ConversationManager.getMaxBackgroundSlots());
        }

        @Override
        public @NotNull ProviderConfigView config() {
            var config = McTalkingConfig.INSTANCE.instance();
            return new ProviderConfigView(McTalkingConfig.hasGeminiApiKey(), config.currentAiModel.getName(),
                    McTalkingConfig.FLASH_MODEL, config.blockingTaskUrgencyMultiplier);
        }

        @Override
        public @NotNull QuotaSnapshot modelQuota(@NotNull String model) {
            return QuotaTracker.snapshot(model);
        }

        @Override
        public @NotNull QuotaSnapshot ttsQuota() {
            return TtsQuotaManager.snapshot();
        }

        @Override
        public long nowMs() {
            return System.currentTimeMillis();
        }
    });

    @Override
    public @NotNull ProviderBudgetView snapshot() {
        return RUNTIME.snapshot();
    }

    @Override
    public @NotNull ProviderConfigView config() {
        return RUNTIME.config();
    }

    @Override
    public @NotNull AddonRegistration registerQuotaListener(@NotNull String id, int order,
                                                           @NotNull ProviderQuotaListener listener) {
        return RUNTIME.registerQuotaListener(id, order, listener);
    }

    @Override
    public void reloadConfig() {
        McTalkingConfig.INSTANCE.load();
    }
}
