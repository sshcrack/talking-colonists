package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Public registry for addon-owned conversation eligibility and urgency rules. */
public final class CitizenConversationRules {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final Pattern RULE_ID = Pattern.compile("[a-z][a-z0-9_]{0,31}:[a-z][a-z0-9_]{0,31}");
    private static final Map<String, SpeechRegistration> SPEECH = new LinkedHashMap<>();
    private static final Map<String, UrgencyRegistration> URGENCY = new LinkedHashMap<>();

    private CitizenConversationRules() {
    }

    public static synchronized @NotNull Registration registerSpeechPolicy(
            @NotNull String id,
            int order,
            @NotNull CitizenSpeechPolicy policy
    ) {
        validateId(id);
        Objects.requireNonNull(policy, "policy");
        if (SPEECH.containsKey(id)) throw new IllegalArgumentException("Speech policy already registered: " + id);
        SpeechRegistration registration = new SpeechRegistration(id, order, policy);
        SPEECH.put(id, registration);
        return new Registration(id, registration, null);
    }

    public static synchronized @NotNull Registration registerUrgencyModifier(
            @NotNull String id,
            int order,
            @NotNull CitizenUrgencyModifier modifier
    ) {
        validateId(id);
        Objects.requireNonNull(modifier, "modifier");
        if (URGENCY.containsKey(id)) throw new IllegalArgumentException("Urgency modifier already registered: " + id);
        UrgencyRegistration registration = new UrgencyRegistration(id, order, modifier);
        URGENCY.put(id, registration);
        return new Registration(id, null, registration);
    }

    /** Called by core after its built-in sleeping/busy/cooldown checks pass. */
    public static boolean addonsAllowSpeech(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull ConversationKind kind
    ) {
        for (SpeechRegistration registration : speechSnapshot()) {
            try {
                if (!registration.policy().canSpeak(citizen, kind)) return false;
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR, "Speech policy " + registration.id() + " failed for " + kind + "; ignoring that policy", t);
            }
        }
        return true;
    }

    /** Called by core after calculating its built-in urgent-contact weight. */
    public static double applyUrgencyModifiers(@NotNull AbstractEntityCitizen citizen, double builtInWeight) {
        double value = Math.max(0.0, builtInWeight);
        for (UrgencyRegistration registration : urgencySnapshot()) {
            double candidate;
            try {
                candidate = registration.modifier().modify(citizen, value);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR, "Urgency modifier " + registration.id() + " failed; ignoring that modifier", t);
                continue;
            }
            if (!Double.isFinite(candidate) || candidate < 0.0) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Urgency modifier {0} returned invalid value {1}; ignoring it", registration.id(), candidate);
                continue;
            }
            value = candidate;
        }
        return value;
    }

    private static void validateId(String id) {
        Objects.requireNonNull(id, "id");
        if (!RULE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Rule id must be namespaced and match " + RULE_ID.pattern() + ": " + id);
        }
    }

    private static synchronized List<SpeechRegistration> speechSnapshot() {
        List<SpeechRegistration> registrations = new ArrayList<>(SPEECH.values());
        registrations.sort(Comparator.comparingInt(SpeechRegistration::order).thenComparing(SpeechRegistration::id));
        return List.copyOf(registrations);
    }

    private static synchronized List<UrgencyRegistration> urgencySnapshot() {
        List<UrgencyRegistration> registrations = new ArrayList<>(URGENCY.values());
        registrations.sort(Comparator.comparingInt(UrgencyRegistration::order).thenComparing(UrgencyRegistration::id));
        return List.copyOf(registrations);
    }

    private record SpeechRegistration(String id, int order, CitizenSpeechPolicy policy) {
    }

    private record UrgencyRegistration(String id, int order, CitizenUrgencyModifier modifier) {
    }

    /** Registration lifetime handle. Safe to close more than once. */
    public static final class Registration implements AutoCloseable {
        private final String id;
        private final SpeechRegistration speech;
        private final UrgencyRegistration urgency;
        private boolean closed;

        private Registration(String id, SpeechRegistration speech, UrgencyRegistration urgency) {
            this.id = id;
            this.speech = speech;
            this.urgency = urgency;
        }

        public String id() {
            return id;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            synchronized (CitizenConversationRules.class) {
                if (speech != null && SPEECH.get(id) == speech) SPEECH.remove(id);
                if (urgency != null && URGENCY.get(id) == urgency) URGENCY.remove(id);
            }
        }
    }
}
