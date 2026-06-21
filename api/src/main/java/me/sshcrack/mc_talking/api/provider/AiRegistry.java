package me.sshcrack.mc_talking.api.provider;

import me.sshcrack.mc_talking.api.session.QuotaManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiRegistry {
    private static final Map<String, AiProvider> providers = new LinkedHashMap<>();
    private static final Map<String, PresetDefinition> presets = new LinkedHashMap<>();
    private static final Map<String, QuotaManager> quotaManagers = new LinkedHashMap<>();
    private static final Map<String, List<ConfigField>> providerConfigFields = new LinkedHashMap<>();

    private AiRegistry() {}

    public static synchronized void register(AiProvider provider) {
        providers.put(provider.id(), provider);
    }

    public static synchronized void registerPreset(PresetDefinition preset) {
        presets.put(preset.fullId(), preset);
    }

    public static synchronized void registerQuotaManager(String providerId, QuotaManager manager) {
        quotaManagers.put(providerId, manager);
    }

    public static synchronized void registerConfigFields(String modId, List<ConfigField> fields) {
        providerConfigFields.put(modId, List.copyOf(fields));
    }

    @Nullable
    public static synchronized AiProvider getProvider(String id) {
        return providers.get(id);
    }

    @Nullable
    public static synchronized PresetDefinition getPreset(String fullId) {
        return presets.get(fullId);
    }

    public static synchronized Map<String, AiProvider> getProviders() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    public static synchronized Map<String, PresetDefinition> getPresets() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(presets));
    }

    @Nullable
    public static synchronized QuotaManager getQuotaManager(String providerId) {
        return quotaManagers.get(providerId);
    }

    public static synchronized Map<String, List<ConfigField>> getProviderConfigFields() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(providerConfigFields));
    }

    public static synchronized List<PresetDefinition> getPresetsByModId(String modId) {
        List<PresetDefinition> result = new ArrayList<>();
        for (PresetDefinition preset : presets.values()) {
            if (modId.equals(preset.sourceModId())) {
                result.add(preset);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void clear() {
        providers.clear();
        presets.clear();
        quotaManagers.clear();
        providerConfigFields.clear();
    }
}
