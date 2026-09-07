package me.sshcrack.mc_talking.api.conversation;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

/**
 * Resource and fairness limits for optional autonomous floor selection.
 *
 * <p>Automatic discussions always use exactly one provider connection at a time and never allow
 * the same citizen to take two consecutive completed turns. Silent participants consume no
 * provider connection. The duration bound is checked before scheduling each turn; the currently
 * audible turn is allowed to finish through the normal controlled-session drain/cancellation
 * lifecycle.</p>
 *
 * @param maxTurns maximum successfully audible citizen turns before automatic control completes
 * @param maxDuration maximum time for which automatic control may schedule new turns
 * @param maxResponseTokens provider-enforced {@code maxOutputTokens} ceiling for each automatic turn
 */
public record AutonomousDiscussionPolicy(
        int maxTurns,
        @NotNull Duration maxDuration,
        int maxResponseTokens
) {
    public static final int DEFAULT_MAX_TURNS = 8;
    public static final Duration DEFAULT_MAX_DURATION = Duration.ofMinutes(2);
    public static final int DEFAULT_MAX_RESPONSE_TOKENS = 256;

    public static final int MAX_TURNS = 64;
    public static final Duration MAX_DURATION = Duration.ofMinutes(15);
    public static final int MIN_RESPONSE_TOKENS = 32;
    public static final int MAX_RESPONSE_TOKENS = 2_048;

    public AutonomousDiscussionPolicy {
        maxDuration = Objects.requireNonNull(maxDuration, "maxDuration");
        if (maxTurns < 1 || maxTurns > MAX_TURNS) {
            throw new IllegalArgumentException("maxTurns must be within [1, " + MAX_TURNS + "]");
        }
        if (maxDuration.isZero() || maxDuration.isNegative() || maxDuration.compareTo(MAX_DURATION) > 0) {
            throw new IllegalArgumentException("maxDuration must be positive and at most " + MAX_DURATION);
        }
        if (maxResponseTokens < MIN_RESPONSE_TOKENS || maxResponseTokens > MAX_RESPONSE_TOKENS) {
            throw new IllegalArgumentException("maxResponseTokens must be within [" + MIN_RESPONSE_TOKENS
                    + ", " + MAX_RESPONSE_TOKENS + "]");
        }
    }

    public static @NotNull AutonomousDiscussionPolicy defaults() {
        return new AutonomousDiscussionPolicy(
                DEFAULT_MAX_TURNS,
                DEFAULT_MAX_DURATION,
                DEFAULT_MAX_RESPONSE_TOKENS
        );
    }
}
