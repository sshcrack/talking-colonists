package me.sshcrack.mc_talking.api.tool;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Registry for addon-provided Gemini tools.
 *
 * <p>Addons register a namespaced ID such as {@code colonist_errands:come_here}. Talking Colonists
 * maps that ID to a collision-free Gemini function name. Addons should persist/use the namespaced
 * ID, never the provider-facing function name.</p>
 */
public final class AiToolRegistry {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final Pattern ID_PART = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final int MAX_PROVIDER_NAME_LENGTH = 64;
    private static final Map<String, RegisteredAiTool> BY_ID = new LinkedHashMap<>();
    private static final Map<String, RegisteredAiTool> BY_PROVIDER_NAME = new LinkedHashMap<>();

    private AiToolRegistry() {
    }

    /**
     * Registers one addon tool. Duplicate namespaced IDs or provider names are rejected.
     *
     * @return a handle that unregisters exactly this registration when closed
     */
    public static synchronized @NotNull Registration register(
            @NotNull String namespace,
            @NotNull String name,
            @NotNull AiTool tool
    ) {
        Objects.requireNonNull(tool, "tool");
        validatePart("namespace", namespace);
        validatePart("name", name);

        String id = namespace + ":" + name;
        // Prefix the namespace length so arbitrary underscore placement cannot create collisions.
        String providerName = "tc_" + namespace.length() + "_" + namespace + "_" + name;
        if (providerName.length() > MAX_PROVIDER_NAME_LENGTH) {
            throw new IllegalArgumentException("Tool provider name is too long after namespacing: " + id);
        }
        if (BY_ID.containsKey(id)) {
            throw new IllegalArgumentException("An addon tool is already registered as " + id);
        }
        if (BY_PROVIDER_NAME.containsKey(providerName)) {
            throw new IllegalStateException("Provider tool-name collision for " + id);
        }

        RegisteredAiTool registered = new RegisteredAiTool(id, providerName, tool);
        BY_ID.put(id, registered);
        BY_PROVIDER_NAME.put(providerName, registered);
        LOGGER.log(System.Logger.Level.INFO, "Registered addon AI tool {0} as {1}", id, providerName);
        return new Registration(registered);
    }

    public static synchronized @Nullable RegisteredAiTool findByProviderName(@NotNull String providerName) {
        return BY_PROVIDER_NAME.get(providerName);
    }

    public static synchronized @Nullable RegisteredAiTool findById(@NotNull String id) {
        return BY_ID.get(id);
    }

    /** Returns a deterministic registration-order snapshot. */
    public static synchronized @NotNull List<RegisteredAiTool> registeredTools() {
        return List.copyOf(new ArrayList<>(BY_ID.values()));
    }

    private static void validatePart(String label, String value) {
        Objects.requireNonNull(value, label);
        if (!ID_PART.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must match " + ID_PART.pattern() + ": " + value);
        }
    }

    public record RegisteredAiTool(
            @NotNull String id,
            @NotNull String providerName,
            @NotNull AiTool tool
    ) {
    }

    /** Registration lifetime handle. Safe to close more than once. */
    public static final class Registration implements AutoCloseable {
        private final RegisteredAiTool registered;
        private boolean closed;

        private Registration(RegisteredAiTool registered) {
            this.registered = registered;
        }

        public @NotNull String id() {
            return registered.id();
        }

        public @NotNull String providerName() {
            return registered.providerName();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            synchronized (AiToolRegistry.class) {
                if (BY_ID.get(registered.id()) == registered) {
                    BY_ID.remove(registered.id());
                    BY_PROVIDER_NAME.remove(registered.providerName());
                    LOGGER.log(System.Logger.Level.INFO, "Unregistered addon AI tool {0}", registered.id());
                }
            }
        }
    }
}
