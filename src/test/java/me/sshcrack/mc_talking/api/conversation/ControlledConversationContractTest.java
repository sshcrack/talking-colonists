package me.sshcrack.mc_talking.api.conversation;

import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import org.junit.jupiter.api.Test;

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
}
