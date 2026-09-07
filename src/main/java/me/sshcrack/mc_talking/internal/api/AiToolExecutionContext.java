package me.sshcrack.mc_talking.internal.api;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.tool.AiToolContext;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Runtime implementation of the addon-facing authoritative tool context. */
public record AiToolExecutionContext(
        @NotNull UUID sessionId,
        @Nullable UUID turnId,
        @NotNull AbstractEntityCitizen citizen,
        @NotNull IColony colony,
        @Nullable ServerPlayer player
) implements AiToolContext {
    public AiToolExecutionContext {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(citizen, "citizen");
        Objects.requireNonNull(colony, "colony");
    }
}
