package me.sshcrack.mc_talking.api.pregen;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;

/** Immutable context for addon pregeneration prompt transforms. */
public record PregenerationPromptContext(
        @NotNull AbstractEntityCitizen citizen,
        @NotNull PregenerationKind kind
) {
}
