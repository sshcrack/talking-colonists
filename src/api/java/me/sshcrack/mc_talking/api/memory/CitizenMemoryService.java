package me.sshcrack.mc_talking.api.memory;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Supported addon access to citizen memory without casting MineColonies data to Talking Colonists
 * mixin interfaces or mutating internal collections directly.
 */
public final class CitizenMemoryService {
    private CitizenMemoryService() {
    }

    public static boolean addEvent(@NotNull AbstractEntityCitizen citizen, @NotNull String event) {
        return citizen.getCitizenData() != null && addEvent(citizen.getCitizenData(), event);
    }

    public static boolean addEvent(@NotNull ICitizenData citizen, @NotNull String event) {
        return TalkingColonistsApi.backend().addMemoryEvent(citizen, event);
    }

    public static boolean addFact(@NotNull AbstractEntityCitizen citizen, @NotNull String fact) {
        return citizen.getCitizenData() != null && addFact(citizen.getCitizenData(), fact);
    }

    public static boolean addFact(@NotNull ICitizenData citizen, @NotNull String fact) {
        return TalkingColonistsApi.backend().addMemoryFact(citizen, fact);
    }

    public static @NotNull Optional<CitizenMemorySnapshot> snapshot(@NotNull ICitizenData citizen) {
        return TalkingColonistsApi.backend().memorySnapshot(citizen);
    }
}
