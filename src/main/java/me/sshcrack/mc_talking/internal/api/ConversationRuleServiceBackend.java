package me.sshcrack.mc_talking.internal.api;

import me.sshcrack.mc_talking.api.conversation.CitizenSpeechPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenUrgencyModifier;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import me.sshcrack.mc_talking.api.service.ConversationRuleService;
import org.jetbrains.annotations.NotNull;

final class ConversationRuleServiceBackend implements ConversationRuleService {
    @Override
    public @NotNull AddonRegistration registerSpeechPolicy(@NotNull String id, int order,
                                                            @NotNull CitizenSpeechPolicy policy) {
        return ConversationRuleRuntime.registerSpeechPolicy(id, order, policy);
    }

    @Override
    public @NotNull AddonRegistration registerUrgencyModifier(@NotNull String id, int order,
                                                               @NotNull CitizenUrgencyModifier modifier) {
        return ConversationRuleRuntime.registerUrgencyModifier(id, order, modifier);
    }
}
