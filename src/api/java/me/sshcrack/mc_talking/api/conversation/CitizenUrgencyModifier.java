package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;

/**
 * Adjusts the built-in urgent-contact weight using addon-owned state.
 *
 * <p>Modifiers run synchronously on the Minecraft server thread in deterministic registration order.
 * They should be fast and side-effect free. Return zero to suppress an urgent contact. Negative and
 * non-finite results are rejected by core.</p>
 */
@FunctionalInterface
public interface CitizenUrgencyModifier {
    double modify(@NotNull AbstractEntityCitizen citizen, double currentWeight);
}
