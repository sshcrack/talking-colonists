package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

/** Detailed result of evaluating whether a citizen may enter a conversation kind. */
public record ConversationEligibility(@NotNull Status status, @NotNull String detail) {
    public enum Status {
        ELIGIBLE,
        SLEEPING,
        VISITOR,
        COOLDOWN,
        BUSY,
        ADDON_POLICY_VETO
    }

    public boolean eligible() {
        return status == Status.ELIGIBLE;
    }

    public static ConversationEligibility eligibleResult() {
        return new ConversationEligibility(Status.ELIGIBLE, "eligible");
    }

    public static ConversationEligibility rejected(Status status, String detail) {
        if (status == Status.ELIGIBLE) throw new IllegalArgumentException("Use eligibleResult() for ELIGIBLE");
        return new ConversationEligibility(status, detail == null ? status.name().toLowerCase() : detail);
    }
}
