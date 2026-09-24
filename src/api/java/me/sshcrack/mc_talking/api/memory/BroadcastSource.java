package me.sshcrack.mc_talking.api.memory;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Who a broadcast comes from. Prompts attribute the message to this source, for example "the notice
 * board announced ..." for a block source instead of naming a player.
 *
 * @param kind           source category
 * @param playerId       sending player for {@link Kind#PLAYER}, or null when unknown
 * @param addonNamespace publishing addon's namespace for {@link Kind#ADDON}
 * @param position       source block for {@link Kind#BLOCK}
 * @param displayName    how citizens refer to the source, e.g. a player name or "the notice board"
 */
public record BroadcastSource(
        @NotNull Kind kind,
        @Nullable UUID playerId,
        @Nullable String addonNamespace,
        @Nullable BlockPos position,
        @NotNull String displayName
) {
    public enum Kind {
        PLAYER,
        ADDON,
        BLOCK
    }

    public BroadcastSource {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        if (kind == Kind.ADDON && (addonNamespace == null || addonNamespace.isBlank())) {
            throw new IllegalArgumentException("ADDON sources need an addon namespace");
        }
        if (kind == Kind.BLOCK && position == null) {
            throw new IllegalArgumentException("BLOCK sources need a position");
        }
        if (position != null) position = position.immutable();
    }

    public static @NotNull BroadcastSource player(@Nullable UUID playerId, @NotNull String playerName) {
        return new BroadcastSource(Kind.PLAYER, playerId, null, null, playerName);
    }

    public static @NotNull BroadcastSource addon(@NotNull String addonNamespace, @NotNull String displayName) {
        return new BroadcastSource(Kind.ADDON, null, addonNamespace, null, displayName);
    }

    public static @NotNull BroadcastSource block(@NotNull BlockPos position, @NotNull String displayName) {
        return new BroadcastSource(Kind.BLOCK, null, null, position, displayName);
    }
}
