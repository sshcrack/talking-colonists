package me.sshcrack.mc_talking.api.conversation;

import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledConversationContractTest {
    @Test
    void controlledPromptContextRequiresPairedSessionAndTurnIdentity() {
        UUID sessionId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        PromptSessionContext context = PromptSessionContext.controlled(
                sessionId, turnId, "Agenda", false, Set.of("meetings:record_vote"));
        assertTrue(context.isControlledTurn());
        assertEquals(sessionId, context.sessionId());
        assertEquals(turnId, context.turnId());

        assertThrows(IllegalArgumentException.class,
                () -> new PromptSessionContext(sessionId, null, "Agenda", false, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new PromptSessionContext(null, turnId, "Agenda", false, Set.of()));
    }

    @Test
    void controlledToolAllowListUsesPublicNamespacedIds() {
        ControlledConversationOptions options = ControlledConversationOptions.allowAddonTools(
                Set.of("meetings:record_vote"));
        assertTrue(options.allowsAddonTool("meetings:record_vote"));

        assertThrows(IllegalArgumentException.class,
                () -> ControlledConversationOptions.allowAddonTools(Set.of("not_namespaced")));
        assertThrows(IllegalArgumentException.class,
                () -> ControlledConversationOptions.allowAddonTools(Set.of("Bad:Name")));
    }
    @Test
    void autonomousDiscussionPolicyHasBoundedDefaultsAndRejectsUnboundedValues() {
        AutonomousDiscussionPolicy defaults = AutonomousDiscussionPolicy.defaults();
        assertEquals(8, defaults.maxTurns());
        assertEquals(Duration.ofMinutes(2), defaults.maxDuration());
        assertEquals(256, defaults.maxResponseTokens());

        assertThrows(IllegalArgumentException.class,
                () -> new AutonomousDiscussionPolicy(0, Duration.ofMinutes(1), 600));
        assertThrows(IllegalArgumentException.class,
                () -> new AutonomousDiscussionPolicy(1, Duration.ofMinutes(16), 600));
        assertThrows(IllegalArgumentException.class,
                () -> new AutonomousDiscussionPolicy(1, Duration.ofMinutes(1), 31));
    }

}
