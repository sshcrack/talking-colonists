package me.sshcrack.mc_talking.api.conversation;

import java.util.Objects;

/** Credential-free, instantaneous provider status; not a conversation ownership lease. */
public record ProviderSessionStatus(State state, boolean readyForInput, int recoveryAttempts) {
    public enum State {
        NEW, CONNECTING, SETTING_UP, ACTIVE, RECOVERING, CLOSED, TERMINAL_ERROR, QUOTA_EXCEEDED
    }

    public ProviderSessionStatus {
        Objects.requireNonNull(state, "state");
        if (recoveryAttempts < 0) throw new IllegalArgumentException("recoveryAttempts must be non-negative");
        if (readyForInput && state != State.ACTIVE) {
            throw new IllegalArgumentException("Only ACTIVE providers can be ready for input");
        }
    }
}
