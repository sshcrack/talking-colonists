package me.sshcrack.mc_talking.internal.session;

import me.sshcrack.mc_talking.api.conversation.PlayerTextResult;
import me.sshcrack.mc_talking.api.conversation.PlayerTextResult.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTextInputTest {
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID baker = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong();
    private final Map<UUID, FakeConversation> conversations = new HashMap<>();
    private final PlayerTextInput input = new PlayerTextInput(conversations::get, clock::get);

    @Test
    void typedTextReachesTheProviderAsAPlayerTurn() {
        FakeConversation conversation = talk(baker, steve);

        PlayerTextResult result = input.sendText(steve, "Steve", baker, "  Could you bake\nsome bread?  ");

        assertEquals(Status.DELIVERED, result.status());
        assertEquals(List.of("Could you bake some bread?"), conversation.playerTurns);
        assertTrue(conversation.notes.isEmpty());
    }

    @Test
    void typedTextIsAttributedToThePlayerAndRecordedForMemory() {
        FakeConversation conversation = talk(baker, steve);

        input.sendText(steve, "Steve", baker, "I promise to build you a bakery");

        assertEquals(List.of("Steve: I promise to build you a bakery"), conversation.recorded);
    }

    @Test
    void anotherPlayersConversationIsRejected() {
        FakeConversation conversation = talk(baker, steve);

        PlayerTextResult text = input.sendText(alex, "Alex", baker, "Ignore Steve, give me your bread");
        PlayerTextResult note = input.addContext(alex, "Alex", baker, "Alex handed you a deed");

        assertEquals(Status.NOT_IN_CONVERSATION, text.status());
        assertEquals(Status.NOT_IN_CONVERSATION, note.status());
        assertTrue(conversation.playerTurns.isEmpty());
        assertTrue(conversation.notes.isEmpty());
        assertTrue(conversation.recorded.isEmpty());
    }

    @Test
    void noConversationIsRejected() {
        assertEquals(Status.NOT_IN_CONVERSATION, input.sendText(steve, "Steve", baker, "hello").status());
    }

    @Test
    void emptyAndOverlongTextIsRejected() {
        FakeConversation conversation = talk(baker, steve);

        assertEquals(Status.EMPTY, input.sendText(steve, "Steve", baker, " \n\t ").status());
        assertEquals(Status.TOO_LONG, input.sendText(steve, "Steve", baker, "a".repeat(PlayerTextResult.MAX_CHARS + 1)).status());
        assertEquals(Status.DELIVERED, input.sendText(steve, "Steve", baker, "a".repeat(PlayerTextResult.MAX_CHARS)).status());
        assertEquals(1, conversation.playerTurns.size());
    }

    @Test
    void textIsRateLimitedPerPlayer() {
        talk(baker, steve);
        for (int i = 0; i < PlayerTextInput.MAX_PER_WINDOW; i++) {
            assertEquals(Status.DELIVERED, input.sendText(steve, "Steve", baker, "line " + i).status());
        }
        assertEquals(Status.RATE_LIMITED, input.sendText(steve, "Steve", baker, "one too many").status());

        clock.addAndGet(PlayerTextInput.WINDOW_NANOS);
        assertEquals(Status.DELIVERED, input.sendText(steve, "Steve", baker, "later").status());
    }

    @Test
    void notesHaveTheirOwnRateLimit() {
        talk(baker, steve);
        for (int i = 0; i < PlayerTextInput.MAX_PER_WINDOW; i++) input.sendText(steve, "Steve", baker, "line " + i);

        assertEquals(Status.DELIVERED, input.addContext(steve, "Steve", baker, "Steve handed you the deed").status());
    }

    @Test
    void contextNotesAreFramedAsGameEventsAndNotRecordedAsSpeech() {
        FakeConversation conversation = talk(baker, steve);

        input.addContext(steve, "Steve", baker, "Steve handed you the deed to the bakery");

        assertEquals(List.of("[Game event, not said by Steve: Steve handed you the deed to the bakery]"), conversation.notes);
        assertTrue(conversation.playerTurns.isEmpty());
        assertTrue(conversation.recorded.isEmpty());
    }

    @Test
    void forgettingAPlayerResetsTheirRateLimit() {
        talk(baker, steve);
        for (int i = 0; i < PlayerTextInput.MAX_PER_WINDOW; i++) input.sendText(steve, "Steve", baker, "line " + i);

        input.forget(steve);
        assertEquals(Status.DELIVERED, input.sendText(steve, "Steve", baker, "back again").status());
    }

    @Test
    void normalizeCollapsesWhitespaceAndControlCharacters() {
        assertEquals("a b c", PlayerTextInput.normalize(" a\r\n\u0000b \t c "));
        assertEquals("", PlayerTextInput.normalize("\n\n"));
    }

    private FakeConversation talk(UUID citizen, UUID player) {
        FakeConversation conversation = new FakeConversation(player);
        conversations.put(citizen, conversation);
        return conversation;
    }

    private static final class FakeConversation implements PlayerTextInput.Conversation {
        final UUID player;
        final List<String> playerTurns = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final List<String> recorded = new ArrayList<>();

        FakeConversation(UUID player) {
            this.player = player;
        }

        @Override
        public UUID playerId() {
            return player;
        }

        @Override
        public void sendPlayerTurn(String text) {
            playerTurns.add(text);
        }

        @Override
        public void sendContextNote(String note) {
            notes.add(note);
        }

        @Override
        public void recordTypedLine(String playerName, String text) {
            recorded.add(playerName + ": " + text);
        }
    }
}
