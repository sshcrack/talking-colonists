package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Snapshot emitted when a core-managed audible conversation starts or ends for a citizen. */
public record ConversationLifecycleEvent(
        @NotNull Phase phase,
        @NotNull ConversationKind kind,
        @NotNull AbstractEntityCitizen citizen,
        @Nullable UUID playerId,
        long gameTimeTicks
) {
    public enum Phase { STARTED, ENDED }
}
