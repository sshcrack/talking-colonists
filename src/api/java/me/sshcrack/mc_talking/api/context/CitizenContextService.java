package me.sshcrack.mc_talking.api.context;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import me.sshcrack.mc_talking.api.prompt.view.CitizenPromptView;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Read-only access to the normalized citizen context Talking Colonists uses for prompting. */
public final class CitizenContextService {
    private CitizenContextService() {
    }

    /** Captures current context without a speaking player. */
    public static @NotNull CitizenPromptView snapshot(@NotNull AbstractEntityCitizen citizen) {
        return snapshot(citizen, null);
    }

    /**
     * Captures current context and, when supplied, authenticated player relation/state information.
     * The returned value is immutable/read-only and does not expose Talking Colonists internals.
     */
    public static @NotNull CitizenPromptView snapshot(
            @NotNull AbstractEntityCitizen citizen,
            @Nullable ServerPlayer speakingPlayer
    ) {
        return TalkingColonistsApi.services().context().snapshot(citizen, speakingPlayer);
    }
}
