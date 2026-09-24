package me.sshcrack.mc_talking.api.text;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.ApiFeature;
import me.sshcrack.mc_talking.api.TalkingColonistsApi;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * In-character text generation for addons (roadmap A3, {@link ApiFeature#TEXT_GENERATION}): letters,
 * notices, newspaper articles, speeches. Text only: it never plays audio and never occupies a
 * conversation slot.
 *
 * <p>Requests use the cheap Flash text model, the same prompt context a citizen gets in
 * conversation (including prompt contributors), and the server's configured response language.
 * Futures always complete with a {@link TextResult}; failures are typed, never thrown. Complete work
 * on the server thread before touching game state from a callback, e.g. via
 * {@code server.execute(...)}.</p>
 */
public final class CitizenTextService {
    private CitizenTextService() {
    }

    /** Text written in the voice of {@code citizen}. Call on the server thread. */
    public static @NotNull CompletableFuture<TextResult> generate(@NotNull AbstractEntityCitizen citizen,
                                                                  @NotNull TextRequest request) {
        TalkingColonistsApi.requireSupported(ApiFeature.TEXT_GENERATION);
        return TalkingColonistsApi.services().text().generate(citizen, request);
    }

    /**
     * Text written in the colony's collective voice (a newspaper, a notice board reply), using the
     * colony's context but no single citizen's. Call on the server thread.
     */
    public static @NotNull CompletableFuture<TextResult> generateColonyVoice(@NotNull IColony colony,
                                                                             @NotNull TextRequest request) {
        TalkingColonistsApi.requireSupported(ApiFeature.TEXT_GENERATION);
        return TalkingColonistsApi.services().text().generateColonyVoice(colony, request);
    }
}
