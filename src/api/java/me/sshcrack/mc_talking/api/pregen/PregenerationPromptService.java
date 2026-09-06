package me.sshcrack.mc_talking.api.pregen;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Registry for safe addon transforms of pregenerated line directives. */
public final class PregenerationPromptService {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final Pattern MODIFIER_ID = Pattern.compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9_]{0,31}");
    private static final int MAX_PROMPT_CHARS = 16_000;
    private static final Map<String, ModifierRegistration> MODIFIERS = new LinkedHashMap<>();

    private PregenerationPromptService() {
    }

    public static synchronized @NotNull Registration registerModifier(
            @NotNull String id,
            int order,
            @NotNull PregenerationPromptModifier modifier
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(modifier, "modifier");
        if (!MODIFIER_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Modifier id must be namespaced and match "
                    + MODIFIER_ID.pattern() + ": " + id);
        }
        if (MODIFIERS.containsKey(id)) {
            throw new IllegalArgumentException("Pregeneration prompt modifier already registered: " + id);
        }
        ModifierRegistration registration = new ModifierRegistration(id, order, modifier);
        MODIFIERS.put(id, registration);
        return new Registration(registration);
    }

    /** Called by core immediately before constructing the pregeneration client. */
    public static @NotNull String apply(
            @NotNull PregenerationPromptContext context,
            @NotNull String originalPrompt
    ) {
        String prompt = Objects.requireNonNull(originalPrompt, "originalPrompt");
        for (ModifierRegistration registration : snapshot()) {
            try {
                String modified = registration.modifier().modify(context, prompt);
                if (modified == null || modified.isBlank()) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Pregeneration prompt modifier {0} returned an empty prompt; ignoring it", registration.id());
                    continue;
                }
                if (modified.length() > MAX_PROMPT_CHARS) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Pregeneration prompt modifier {0} exceeded {1} chars; truncating",
                            registration.id(), MAX_PROMPT_CHARS);
                    modified = modified.substring(0, MAX_PROMPT_CHARS);
                }
                prompt = modified;
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Pregeneration prompt modifier " + registration.id() + " failed and was skipped", t);
            }
        }
        return prompt;
    }

    private static synchronized List<ModifierRegistration> snapshot() {
        List<ModifierRegistration> result = new ArrayList<>(MODIFIERS.values());
        result.sort(Comparator.comparingInt(ModifierRegistration::order).thenComparing(ModifierRegistration::id));
        return List.copyOf(result);
    }

    private record ModifierRegistration(String id, int order, PregenerationPromptModifier modifier) {
    }

    public static final class Registration implements AutoCloseable {
        private final ModifierRegistration registration;
        private boolean closed;

        private Registration(ModifierRegistration registration) {
            this.registration = registration;
        }

        public String id() {
            return registration.id();
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            synchronized (PregenerationPromptService.class) {
                if (MODIFIERS.get(registration.id()) == registration) {
                    MODIFIERS.remove(registration.id());
                }
            }
        }
    }
}
