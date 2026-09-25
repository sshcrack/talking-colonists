package me.sshcrack.mc_talking.api.intro;

import com.minecolonies.api.colony.IColony;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/** When an {@link Introduction} becomes relevant for a player. */
@FunctionalInterface
public interface IntroductionTrigger {
    /**
     * Whether the introduction is relevant now for {@code player}, who stands in {@code colony} and is
     * one of its members. Runs on the server thread every few seconds per player until it returns true
     * and the introduction happened, so keep it cheap. Exceptions count as "not now".
     */
    boolean isDue(@NotNull ServerPlayer player, @NotNull IColony colony);
}
