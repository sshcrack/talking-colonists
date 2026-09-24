package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.conversation.CitizenSpeechPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenUrgencyModifier;
import me.sshcrack.mc_talking.api.conversation.VisitorSpeechPolicy;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime service for addon conversation eligibility/urgency rules. */
public interface ConversationRuleService {
    @NotNull AddonRegistration registerSpeechPolicy(@NotNull String id, int order, @NotNull CitizenSpeechPolicy policy);
    @NotNull AddonRegistration registerUrgencyModifier(@NotNull String id, int order, @NotNull CitizenUrgencyModifier modifier);

    /** Runtimes without {@link me.sshcrack.mc_talking.api.ApiFeature#VISITOR_SPEAKERS} cannot let visitors speak. */
    default @NotNull AddonRegistration registerVisitorPolicy(@NotNull String id, int order,
                                                             @NotNull VisitorSpeechPolicy policy) {
        throw new UnsupportedOperationException("Visitor speakers are not supported by this runtime");
    }
}
