package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputTurnTrackerTest {
    private final List<UUID> started = new ArrayList<>();
    private final OutputTurnTracker turns = new OutputTurnTracker();

    private UUID ensure() {
        return turns.ensure(started::add);
    }

    @Test
    void oneTurnUntilItCompletesAndTheHeardTranscriptIsReturnedOnce() {
        UUID turn = ensure();
        assertEquals(turn, ensure(), "chunks of one turn share its ID");
        assertEquals(List.of(turn), started);

        turns.setPendingTranscript(turn, "Hello there.");
        assertFalse(turns.completeAudible(turn).completed(), "audio drained before the provider finished");

        turns.markProviderTurnComplete();
        var completion = turns.completeAudible(turn);
        assertTrue(completion.completed());
        assertEquals("Hello there.", completion.heardTranscript());
        assertNull(turns.current());
        assertFalse(turns.completeAudible(turn).completed(), "a turn completes only once");

        assertNotEquals(turn, ensure(), "the next output starts a new turn");
    }

    @Test
    void aTranscriptOfAnotherTurnIsNotCommitted() {
        UUID turn = ensure();
        turns.setPendingTranscript(UUID.randomUUID(), "stale");
        turns.markProviderTurnComplete();
        var completion = turns.completeAudible(turn);
        assertTrue(completion.completed());
        assertNull(completion.heardTranscript());
    }

    @Test
    void providerInterruptionDropsTheTranscriptUntilTheTurnIsRetired() {
        UUID turn = ensure();
        turns.setPendingTranscript(turn, "never heard");
        turns.markInterrupted();
        turns.markProviderTurnComplete();

        assertTrue(turns.isInterrupted());
        assertFalse(turns.completeAudible(turn).completed());
        assertTrue(turns.completeInterrupted(turn));
        assertFalse(turns.isInterrupted(), "retiring clears the interruption for the next turn");
        assertNotEquals(turn, ensure());
    }

    @Test
    void bargeInReportsWhetherTheProviderHadFinished() {
        UUID turn = ensure();
        var early = turns.interruptForBargeIn(turn);
        assertEquals(new OutputTurnTracker.BargeIn(turn, false), early);

        turns.completeInterrupted(turn);
        UUID next = ensure();
        turns.setPendingTranscript(next, "cut off");
        turns.markProviderTurnComplete();
        var late = turns.interruptForBargeIn(next);
        assertEquals(new OutputTurnTracker.BargeIn(next, true), late);
        assertFalse(turns.completeAudible(next).completed(), "barged-in speech never counts as heard");

        assertNull(turns.interruptForBargeIn(UUID.randomUUID()), "a turn that is no longer current is ignored");
    }

    @Test
    void invalidateDropsEverything() {
        assertNull(turns.invalidate(), "nothing to drop");
        UUID turn = ensure();
        turns.setPendingTranscript(turn, "lost on reconnect");
        turns.markProviderTurnComplete();

        assertEquals(turn, turns.invalidate());
        assertNull(turns.current());
        assertFalse(turns.isProviderTurnComplete());
        assertFalse(turns.completeAudible(turn).completed());
        assertFalse(turns.completeInterrupted(turn));
    }
}
