package me.sshcrack.mc_talking.internal.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtteranceTrackerTest {
    private final List<String> events = new ArrayList<>();
    private final UtteranceTracker tracker = new UtteranceTracker(new UtteranceTracker.Sink() {
        @Override
        public void player(String text) { events.add("player: " + text); }

        @Override
        public void citizen(String text) { events.add("citizen: " + text); }
    });

    @Test
    void chunkedInputBecomesOnePlayerUtteranceThenTheCitizenReply() {
        tracker.onInputChunk(" Can you");
        tracker.onInputChunk(" bake some ");
        tracker.onInputChunk("bread?");
        assertTrue(events.isEmpty(), "partial chunks are never delivered");

        tracker.onProviderTurnComplete();
        tracker.onCitizenTurnHeard("  Of course, give me an hour. ");

        assertEquals(List.of("player: Can you bake some bread?", "citizen: Of course, give me an hour."), events);
    }

    @Test
    void eachTurnIsDeliveredOnce() {
        tracker.onInputChunk("Hello");
        tracker.onProviderTurnComplete();
        tracker.onProviderTurnComplete();
        assertEquals(List.of("player: Hello"), events, "no duplicate for a turn without new input");
    }

    @Test
    void citizenOnlyTurnsHaveNoPlayerUtterance() {
        tracker.onProviderTurnComplete();
        tracker.onCitizenTurnHeard("Nice weather today.");
        tracker.onCitizenTurnHeard("   ");
        assertEquals(List.of("citizen: Nice weather today."), events);
    }

    @Test
    void nothingIsDeliveredAfterTheSessionEnds() {
        tracker.onInputChunk("Are you still");
        tracker.end();
        tracker.onInputChunk(" there?");
        tracker.onProviderTurnComplete();
        tracker.onCitizenTurnHeard("Late reply");
        assertTrue(events.isEmpty(), "a closing session reports nothing, was " + events);
    }
}
