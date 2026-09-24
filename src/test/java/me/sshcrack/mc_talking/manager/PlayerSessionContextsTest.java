package me.sshcrack.mc_talking.manager;

import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.PlayerConversationOptions;
import me.sshcrack.mc_talking.api.prompt.PromptSessionContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSessionContextsTest {
    private static final String BASE = "You are Anna, a baker.";

    @Test
    void defaultOptionsMatchAnOrdinaryPlayerConversation() {
        PromptSessionContext context = PlayerSessionContexts.of(PlayerConversationOptions.defaults());

        assertEquals(PromptSessionContext.empty(), context);
        assertEquals(BASE, PlayerSessionContexts.withAgenda(BASE, context));
        assertTrue(context.allowsAddonTool("quests:give_quest"));
    }

    @Test
    void agendaAppearsOnlyInTheSessionThatCarriesIt() {
        PromptSessionContext quest = PlayerSessionContexts.of(
                PlayerConversationOptions.defaults().withAgenda("Offer the player the lost-cat quest."));
        PromptSessionContext later = PlayerSessionContexts.of(PlayerConversationOptions.defaults());

        String questPrompt = PlayerSessionContexts.withAgenda(BASE, quest);
        assertTrue(questPrompt.startsWith(BASE));
        assertTrue(questPrompt.contains("Offer the player the lost-cat quest."));
        assertFalse(PlayerSessionContexts.withAgenda(BASE, later).contains("lost-cat"));
    }

    @Test
    void toolAllowListUsesControlledSessionSemantics() {
        PromptSessionContext onlyQuests = PlayerSessionContexts.of(PlayerConversationOptions.defaults()
                .withTools(ControlledConversationOptions.allowAddonTools(Set.of("quests:give_quest"))));
        PromptSessionContext none = PlayerSessionContexts.of(PlayerConversationOptions.defaults()
                .withTools(ControlledConversationOptions.noAddonTools()));

        assertTrue(onlyQuests.allowsAddonTool("quests:give_quest"));
        assertFalse(onlyQuests.allowsAddonTool("errands:fetch_item"));
        assertFalse(none.allowsAddonTool("quests:give_quest"));
        assertFalse(onlyQuests.isControlledTurn());
    }

    @Test
    void controlledTurnsKeepTheirOwnAgendaHandling() {
        PromptSessionContext controlled = PromptSessionContext.controlled(
                UUID.randomUUID(), UUID.randomUUID(), "Debate the harvest.", true, Set.of());

        assertEquals(BASE, PlayerSessionContexts.withAgenda(BASE, controlled));
    }
}
