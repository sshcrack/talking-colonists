package me.sshcrack.mc_talking.api.colony;

import com.minecolonies.api.colony.IColony;
import org.jetbrains.annotations.NotNull;

/** Receives newly recorded colony events on the server thread. Keep it fast; exceptions are logged and ignored. */
@FunctionalInterface
public interface ColonyEventListener {
    void onEvent(@NotNull IColony colony, @NotNull ColonyEventView event);
}
