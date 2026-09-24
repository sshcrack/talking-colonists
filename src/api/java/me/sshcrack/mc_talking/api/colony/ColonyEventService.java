package me.sshcrack.mc_talking.api.colony;

import com.minecolonies.api.colony.IColony;
import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.registration.AddonRegistration;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

/**
 * The colony event feed (roadmap A2, {@link ApiFeature#COLONY_EVENTS}): raids, deaths, births,
 * hires, job changes, building changes and addon events. Citizens mention recent events in
 * conversation for the server's {@code colonyEventWindowSeconds}.
 *
 * <p>Core events are bounded per colony (the 20 most recent). Addon events have a separate budget
 * of the 10 most recent per namespace, so an addon can never push core events out.</p>
 */
public final class ColonyEventService {
    private ColonyEventService() {
    }

    /** Events recorded within {@code maxAge} (in game time), newest first. */
    public static @NotNull List<ColonyEventView> recent(@NotNull IColony colony, @NotNull Duration maxAge) {
        TalkingColonistsApi.requireSupported(ApiFeature.COLONY_EVENTS);
        return TalkingColonistsApi.services().colonyEvents().recent(colony, maxAge);
    }

    /**
     * Records an addon event and notifies listeners. Returns false if the colony is not available.
     * Marshalled to the server thread.
     */
    public static boolean record(@NotNull IColony colony, @NotNull AddonColonyEvent event) {
        TalkingColonistsApi.requireSupported(ApiFeature.COLONY_EVENTS);
        return TalkingColonistsApi.services().colonyEvents().record(colony, event);
    }

    /**
     * Registers a listener for newly recorded events (core and addon) in every colony. Listeners run
     * on the server thread in ascending {@code order}; {@code id} must be namespaced
     * ({@code "myaddon:gazette"}). Close the returned registration to unregister.
     */
    public static @NotNull AddonRegistration registerListener(@NotNull String id, int order,
                                                             @NotNull ColonyEventListener listener) {
        TalkingColonistsApi.requireSupported(ApiFeature.COLONY_EVENTS);
        return TalkingColonistsApi.services().colonyEvents().registerListener(id, order, listener);
    }
}
