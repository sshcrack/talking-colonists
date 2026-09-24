package me.sshcrack.mc_talking.api.memory;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * A colony broadcast to publish through {@link CitizenMemoryService#publishBroadcast}.
 *
 * <p>Use the factories and {@code with...} methods rather than the canonical constructor.
 * {@link BroadcastScope#PROPAGATE_FROM} needs exactly one origin: a citizen or a position.</p>
 *
 * @param message        what citizens remember, at most {@link #MAX_MESSAGE_LENGTH} characters
 * @param source         who the broadcast is attributed to
 * @param scope          how it reaches citizens
 * @param originCitizen  first carrier for {@link BroadcastScope#PROPAGATE_FROM}
 * @param originPosition first carriers are the citizens near this position
 * @param expiresAfter   how long citizens keep it, or null to keep it until it ages out of memory
 * @param announceAloud  whether carriers may announce it aloud to nearby players while it spreads
 */
public record BroadcastRequest(
        @NotNull String message,
        @NotNull BroadcastSource source,
        @NotNull BroadcastScope scope,
        @Nullable UUID originCitizen,
        @Nullable BlockPos originPosition,
        @Nullable Duration expiresAfter,
        boolean announceAloud
) {
    public static final int MAX_MESSAGE_LENGTH = 500;

    public BroadcastRequest {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scope, "scope");
        message = message.strip();
        if (message.isEmpty()) throw new IllegalArgumentException("message must not be blank");
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message exceeds " + MAX_MESSAGE_LENGTH + " characters");
        }
        boolean hasOrigin = originCitizen != null || originPosition != null;
        if (scope == BroadcastScope.COLONY_IMMEDIATE && hasOrigin) {
            throw new IllegalArgumentException("COLONY_IMMEDIATE broadcasts have no origin");
        }
        if (scope == BroadcastScope.PROPAGATE_FROM && (originCitizen == null) == (originPosition == null)) {
            throw new IllegalArgumentException("PROPAGATE_FROM needs exactly one origin: a citizen or a position");
        }
        if (expiresAfter != null && (expiresAfter.isNegative() || expiresAfter.isZero())) {
            throw new IllegalArgumentException("expiresAfter must be positive");
        }
        if (originPosition != null) originPosition = originPosition.immutable();
    }

    /** Every citizen learns the message now. Announced aloud by default. */
    public static @NotNull BroadcastRequest immediate(@NotNull BroadcastSource source, @NotNull String message) {
        return new BroadcastRequest(message, source, BroadcastScope.COLONY_IMMEDIATE, null, null, null, true);
    }

    /** One citizen learns the message and spreads it. Announced aloud by default. */
    public static @NotNull BroadcastRequest fromCitizen(@NotNull BroadcastSource source, @NotNull String message,
                                                        @NotNull UUID citizenId) {
        return new BroadcastRequest(message, source, BroadcastScope.PROPAGATE_FROM,
                Objects.requireNonNull(citizenId, "citizenId"), null, null, true);
    }

    /** The citizens around {@code position} learn the message and spread it. Announced aloud by default. */
    public static @NotNull BroadcastRequest fromPosition(@NotNull BroadcastSource source, @NotNull String message,
                                                         @NotNull BlockPos position) {
        return new BroadcastRequest(message, source, BroadcastScope.PROPAGATE_FROM,
                null, Objects.requireNonNull(position, "position"), null, true);
    }

    public @NotNull BroadcastRequest withExpiry(@Nullable Duration expiresAfter) {
        return new BroadcastRequest(message, source, scope, originCitizen, originPosition, expiresAfter, announceAloud);
    }

    public @NotNull BroadcastRequest withAnnounceAloud(boolean announceAloud) {
        return new BroadcastRequest(message, source, scope, originCitizen, originPosition, expiresAfter, announceAloud);
    }
}
