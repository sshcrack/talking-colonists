package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;

/**
 * Veto-only extension for addon gameplay state that should make a citizen unavailable to a
 * particular kind of conversation (for example, a guard currently holding a defense line).
 *
 * <p>This callback is evaluated on the server thread. It should be fast and side-effect free.</p>
 */
@FunctionalInterface
public interface CitizenSpeechPolicy {
    boolean canSpeak(@NotNull AbstractEntityCitizen citizen, @NotNull ConversationKind kind);
}
