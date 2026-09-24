package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Snapshot emitted when a core-managed audible conversation starts or ends for a citizen.
 * {@code purpose} is the tag an addon passed in {@link PlayerConversationOptions}, else {@code null}.
 */
public record ConversationLifecycleEvent(
        @NotNull Phase phase,
        @NotNull ConversationKind kind,
        @NotNull AbstractEntityCitizen citizen,
        @Nullable UUID playerId,
        @Nullable UUID sessionId,
        @Nullable UUID turnId,
        long gameTimeTicks,
        @Nullable String purpose
) {
    /** Event without an addon purpose tag. */
    public ConversationLifecycleEvent(
            @NotNull Phase phase,
            @NotNull ConversationKind kind,
            @NotNull AbstractEntityCitizen citizen,
            @Nullable UUID playerId,
            @Nullable UUID sessionId,
            @Nullable UUID turnId,
            long gameTimeTicks
    ) {
        this(phase, kind, citizen, playerId, sessionId, turnId, gameTimeTicks, null);
    }

    public ConversationLifecycleEvent {
        if (phase == null) throw new IllegalArgumentException("phase must not be null");
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (citizen == null) throw new IllegalArgumentException("citizen must not be null");
        if ((sessionId == null) != (turnId == null)) {
            throw new IllegalArgumentException("sessionId and turnId must be supplied together");
        }
        if (kind == ConversationKind.CONTROLLED && sessionId == null) {
            throw new IllegalArgumentException("controlled lifecycle events require sessionId and turnId");
        }
    }

    public enum Phase { STARTED, ENDED }
}
