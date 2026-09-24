package me.sshcrack.mc_talking.api.service;

import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.api.colony.AddonColonyEvent;
import me.sshcrack.mc_talking.api.colony.ColonyEventListener;
import me.sshcrack.mc_talking.api.colony.ColonyEventView;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/** Runtime service for the colony event feed (roadmap A2). */
public interface ColonyEventFeedService {
    @NotNull List<ColonyEventView> recent(@NotNull IColony colony, @NotNull Duration maxAge);

    boolean record(@NotNull IColony colony, @NotNull AddonColonyEvent event);

    @NotNull AddonRegistration registerListener(@NotNull String id, int order, @NotNull ColonyEventListener listener);
}
