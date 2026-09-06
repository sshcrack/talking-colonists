package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.tool.AiTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Runtime-only storage and provider-name mapping for addon AI tools. */
public final class AiToolRuntime {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final Pattern ID_PART = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final int MAX_PROVIDER_NAME_LENGTH = 64;
    private static final RegistrationRegistry<RegisteredTool> TOOLS = new RegistrationRegistry<>("AI tool");

    private AiToolRuntime() {
    }

    public static @NotNull AddonRegistration register(
            @NotNull String namespace,
            @NotNull String name,
            @NotNull AiTool tool
    ) {
        validatePart("namespace", namespace);
        validatePart("name", name);
        Objects.requireNonNull(tool, "tool");

        String id = namespace + ":" + name;
        if (TOOLS.find(id) != null) {
            throw new IllegalArgumentException("AI tool already registered: " + id);
        }

        String providerName = "tc_" + namespace.length() + "_" + namespace + "_" + name;
        if (providerName.length() > MAX_PROVIDER_NAME_LENGTH) {
            throw new IllegalArgumentException("Tool provider name is too long after namespacing: " + id);
        }
        if (findByProviderName(providerName) != null) {
            throw new IllegalStateException("Provider tool-name collision for " + id);
        }

        AddonRegistration registration = TOOLS.register(id, 0, new RegisteredTool(id, providerName, tool));
        LOGGER.log(System.Logger.Level.INFO, "Registered addon AI tool {0}", id);
        return registration;
    }

    public static @Nullable RegisteredTool findByProviderName(@NotNull String providerName) {
        for (var entry : TOOLS.registrationSnapshot()) {
            if (entry.value().providerName().equals(providerName)) return entry.value();
        }
        return null;
    }

    public static @Nullable RegisteredTool findById(@NotNull String id) {
        var entry = TOOLS.find(id);
        return entry == null ? null : entry.value();
    }

    public static @NotNull List<RegisteredTool> registeredTools() {
        return TOOLS.registrationSnapshot().stream().map(RegistrationRegistry.Entry::value).toList();
    }

    private static void validatePart(String label, String value) {
        Objects.requireNonNull(value, label);
        if (!ID_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must match " + ID_PART.pattern() + ": " + value);
        }
    }

    public record RegisteredTool(
            @NotNull String id,
            @NotNull String providerName,
            @NotNull AiTool tool
    ) {
    }
}
