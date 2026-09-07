package me.sshcrack.mc_talking.api.conversation;

import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Public registry for addon-owned conversation eligibility and urgency rules. */
public final class CitizenConversationRules {
    private CitizenConversationRules() {
    }

    /**
     * Registers a veto-only speech policy. Policies run in ascending {@code order}, then by ID.
     */
    public static @NotNull AddonRegistration registerSpeechPolicy(
            @NotNull String id,
            int order,
            @NotNull CitizenSpeechPolicy policy
    ) {
        return TalkingColonistsApi.services().conversationRules().registerSpeechPolicy(id, order, policy);
    }

    /**
     * Registers an urgency-weight modifier. Modifiers run in ascending {@code order}, then by ID.
     */
    public static @NotNull AddonRegistration registerUrgencyModifier(
            @NotNull String id,
            int order,
            @NotNull CitizenUrgencyModifier modifier
    ) {
        return TalkingColonistsApi.services().conversationRules().registerUrgencyModifier(id, order, modifier);
    }
}
