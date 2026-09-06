package me.sshcrack.mc_talking.api.tool;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative execution context supplied by Talking Colonists when Gemini calls an addon tool.
 *
 * <p>The player is resolved from the owning conversation by core. Addons must not accept a player
 * UUID, rank, or other authority-bearing identity from model-supplied JSON parameters.</p>
 */
public record AiToolContext(
        @NotNull AbstractEntityCitizen citizen,
        @NotNull IColony colony,
        @Nullable ServerPlayer player
) {
    public boolean hasPlayer() {
        return player != null;
    }

    /**
     * Returns the authenticated initiating player or throws when the tool was called outside a
     * direct player conversation. Prefer {@link AiToolScope#PLAYER_CONVERSATION} for such tools.
     */
    public @NotNull ServerPlayer requirePlayer() {
        if (player == null) {
            throw new IllegalStateException("This tool call has no authenticated player");
        }
        return player;
    }
}
