package me.sshcrack.mc_talking.api.service;

import me.sshcrack.mc_talking.api.conversation.CitizenSpeechPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenUrgencyModifier;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

/** Runtime service for addon conversation eligibility/urgency rules. */
public interface ConversationRuleService {
    @NotNull AddonRegistration registerSpeechPolicy(@NotNull String id, int order, @NotNull CitizenSpeechPolicy policy);
    @NotNull AddonRegistration registerUrgencyModifier(@NotNull String id, int order, @NotNull CitizenUrgencyModifier modifier);
}
