package me.sshcrack.mc_talking.internal.api;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.CitizenSpeechPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenUrgencyModifier;
import me.sshcrack.mc_talking.api.conversation.ConversationKind;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime-only storage/evaluation for addon conversation rules. */
public final class ConversationRuleRuntime {
    private static final System.Logger LOGGER = System.getLogger("mc_talking-api");
    private static final RegistrationRegistry<CitizenSpeechPolicy> SPEECH =
            new RegistrationRegistry<>("Speech policy");
    private static final RegistrationRegistry<CitizenUrgencyModifier> URGENCY =
            new RegistrationRegistry<>("Urgency modifier");

    private ConversationRuleRuntime() {
    }

    public static @NotNull AddonRegistration registerSpeechPolicy(
            @NotNull String id,
            int order,
            @NotNull CitizenSpeechPolicy policy
    ) {
        return SPEECH.register(id, order, policy);
    }

    public static @NotNull AddonRegistration registerUrgencyModifier(
            @NotNull String id,
            int order,
            @NotNull CitizenUrgencyModifier modifier
    ) {
        return URGENCY.register(id, order, modifier);
    }

    public static boolean addonsAllowSpeech(
            @NotNull AbstractEntityCitizen citizen,
            @NotNull ConversationKind kind
    ) {
        for (var registration : SPEECH.orderedSnapshot()) {
            try {
                if (!registration.value().canSpeak(citizen, kind)) return false;
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Speech policy " + registration.id() + " failed for " + kind + "; ignoring that policy", t);
            }
        }
        return true;
    }

    public static double applyUrgencyModifiers(@NotNull AbstractEntityCitizen citizen, double builtInWeight) {
        double value = Math.max(0.0, builtInWeight);
        for (var registration : URGENCY.orderedSnapshot()) {
            double candidate;
            try {
                candidate = registration.value().modify(citizen, value);
            } catch (Throwable t) {
                LOGGER.log(System.Logger.Level.ERROR,
                        "Urgency modifier " + registration.id() + " failed; ignoring that modifier", t);
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
}
