package me.sshcrack.mc_talking.internal.provider;

import me.sshcrack.mc_talking.api.provider.ModelQuotaView;
import me.sshcrack.mc_talking.api.provider.ProviderBudgetView;
import me.sshcrack.mc_talking.api.provider.ProviderConfigView;
import me.sshcrack.mc_talking.api.provider.ProviderQuotaListener;
import me.sshcrack.mc_talking.api.provider.ProviderQuotaState;
import me.sshcrack.mc_talking.api.provider.SlotUsage;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.config.QuotaSnapshot;
import me.sshcrack.mc_talking.config.QuotaStatus;
import me.sshcrack.mc_talking.config.TtsQuotaManager;
import me.sshcrack.mc_talking.internal.registration.RegistrationRegistry;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds provider budget views (roadmap A7) and tells listeners when a model's quota state changes.
 * All game and config access goes through {@link Sources}, so this class is unit tested.
 */
public final class ProviderBudgetRuntime {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");

    /** Where the runtime reads live state from. */
    public interface Sources {
        @NotNull SlotUsage foreground();

        @NotNull SlotUsage background();

        @NotNull ProviderConfigView config();

        @NotNull QuotaSnapshot modelQuota(@NotNull String model);

        @NotNull QuotaSnapshot ttsQuota();

        long nowMs();
    }

    private final Sources sources;
    private final RegistrationRegistry<ProviderQuotaListener> listeners = new RegistrationRegistry<>("Quota listener");
    private final Map<String, ModelQuotaView> lastSeen = new HashMap<>();

    public ProviderBudgetRuntime(@NotNull Sources sources) {
        this.sources = sources;
    }

    public @NotNull ProviderBudgetView snapshot() {
        return new ProviderBudgetView(sources.foreground(), sources.background(), models(),
                Instant.ofEpochMilli(sources.nowMs()));
    }

    public @NotNull ProviderConfigView config() {
        return sources.config();
    }

    public @NotNull AddonRegistration registerQuotaListener(@NotNull String id, int order,
                                                           @NotNull ProviderQuotaListener listener) {
        return listeners.register(id, order, listener);
    }

    /**
     * Compares the current quota states with the last poll and notifies listeners of each change.
     * The first poll only records the states.
     */
    public synchronized void poll() {
        for (ModelQuotaView current : models()) {
            ModelQuotaView previous = lastSeen.put(current.model(), current);
            if (previous == null || previous.equals(current)) continue;
            for (var registration : listeners.orderedSnapshot()) {
                try {
                    registration.value().onQuotaChanged(previous, current);
                } catch (Throwable t) {
                    LOGGER.log(System.Logger.Level.ERROR,
                            "Quota listener " + registration.id() + " failed; ignoring it for this change", t);
                }
            }
        }
    }

    private List<ModelQuotaView> models() {
        ProviderConfigView config = sources.config();
        List<ModelQuotaView> models = new ArrayList<>(3);
        models.add(view(config.liveModel(), sources.modelQuota(config.liveModel()), config.apiKeySet()));
        if (!config.textModel().equals(config.liveModel())) {
            models.add(view(config.textModel(), sources.modelQuota(config.textModel()), config.apiKeySet()));
        }
        models.add(view(TtsQuotaManager.LABEL, sources.ttsQuota(), config.apiKeySet()));
        return models;
    }

    static @NotNull ModelQuotaView view(@NotNull String model, @NotNull QuotaSnapshot quota, boolean apiKeySet) {
        if (!apiKeySet) return new ModelQuotaView(model, ProviderQuotaState.UNKNOWN, null);
        if (quota.status() == QuotaStatus.OK) return new ModelQuotaView(model, ProviderQuotaState.OK, null);
        Instant until = quota.resetAtMs() == null ? null : Instant.ofEpochMilli(quota.resetAtMs());
        return new ModelQuotaView(model, ProviderQuotaState.EXHAUSTED, until);
    }
}
