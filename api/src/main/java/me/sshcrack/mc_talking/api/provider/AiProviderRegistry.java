package me.sshcrack.mc_talking.api.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AiProviderRegistry {
    private static final Map<Capability, List<AiProvider>> providersByCapability = new ConcurrentHashMap<>();
    private static final Map<String, AiProvider> providersById = new ConcurrentHashMap<>();

    // Provider-specific configuration key-value store.
    // Host mods can set configuration values (e.g. API keys) here so providers
    // can read them without compile-time dependencies on each other.
    private static final Map<String, String> globalConfig = new ConcurrentHashMap<>();

    public static void setConfig(String key, String value) {
        if (value == null) {
            globalConfig.remove(key);
        } else {
            globalConfig.put(key, value);
        }
    }

    public static String getConfig(String key) {
        return globalConfig.get(key);
    }

    public static String getConfig(String key, String defaultValue) {
        return globalConfig.getOrDefault(key, defaultValue);
    }

    public static final String CONFIG_GEMINI_API_KEY = "gemini.apiKey";

    private AiProviderRegistry() {
    }

    public static void register(AiProvider provider) {
        String id = provider.providerId();
        if (providersById.putIfAbsent(id, provider) != null) {
            throw new IllegalArgumentException("Provider with ID '" + id + "' is already registered");
        }

        for (Capability capability : provider.capabilities()) {
            providersByCapability
                    .computeIfAbsent(capability, k -> new CopyOnWriteArrayList<>())
                    .add(provider);
        }
    }

    public static void unregister(String providerId) {
        AiProvider provider = providersById.remove(providerId);
        if (provider == null) return;

        for (Capability capability : provider.capabilities()) {
            List<AiProvider> list = providersByCapability.get(capability);
            if (list != null) {
                list.remove(provider);
                if (list.isEmpty()) {
                    providersByCapability.remove(capability);
                }
            }
        }
    }

    public static Optional<AiProvider> getProvider(String providerId, Capability capability) {
        AiProvider provider = providersById.get(providerId);
        if (provider != null && provider.supports(capability)) {
            return Optional.of(provider);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static <T extends AiProvider> Optional<T> getProvider(String providerId, Class<T> type) {
        AiProvider provider = providersById.get(providerId);
        if (provider != null && type.isInstance(provider)) {
            return Optional.of((T) provider);
        }
        return Optional.empty();
    }

    public static List<AiProvider> getProviders(Capability capability) {
        List<AiProvider> providers = providersByCapability.get(capability);
        if (providers == null) return List.of();

        List<AiProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(AiProvider::priority));
        return Collections.unmodifiableList(sorted);
    }

    public static Set<String> getProviderIds(Capability capability) {
        List<AiProvider> providers = providersByCapability.get(capability);
        if (providers == null) return Set.of();

        Set<String> ids = new HashSet<>();
        for (AiProvider p : providers) {
            ids.add(p.providerId());
        }
        return ids;
    }

    public static Optional<AiProvider> firstForCapability(Capability capability) {
        List<AiProvider> providers = providersByCapability.get(capability);
        if (providers == null || providers.isEmpty()) return Optional.empty();
        return Optional.of(providers.get(0));
    }

    public static Optional<AiProvider> firstOtherAvailable(Capability capability, String excludeProviderId) {
        List<AiProvider> providers = providersByCapability.get(capability);
        if (providers == null) return Optional.empty();
        for (AiProvider p : providers) {
            if (!p.providerId().equals(excludeProviderId)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static boolean hasCapability(Capability capability) {
        List<AiProvider> providers = providersByCapability.get(capability);
        return providers != null && !providers.isEmpty();
    }

    public static void clear() {
        providersByCapability.clear();
        providersById.clear();
    }

    public static Collection<AiProvider> allProviders() {
        return Collections.unmodifiableCollection(providersById.values());
    }
}
