package me.sshcrack.mc_talking.api.conversation;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import org.jetbrains.annotations.NotNull;

/**
 * Opt-in rule that lets MineColonies visitors (tavern guests) speak. Visitors are rejected with
 * {@link ConversationEligibility.Status#VISITOR} unless at least one registered policy allows the
 * requested kind. Core ambient chatter never picks visitors on its own, so allowing
 * {@link ConversationKind#PLAYER} is what makes a visitor answer players.
 *
 * <p>Evaluated on the server thread; it should be fast and side-effect free. Veto-only
 * {@link CitizenSpeechPolicy} rules still apply to allowed visitors.</p>
 */
@FunctionalInterface
public interface VisitorSpeechPolicy {
    boolean allowsVisitor(@NotNull AbstractEntityCitizen visitor, @NotNull ConversationKind kind);
}
