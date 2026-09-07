package me.sshcrack.mc_talking.internal.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackTurnGateTest {
    @Test
    void bargeInStopsOnceDiscardsQueuedAudioAndRejectsLateChunks() {
        var gate = new PlaybackTurnGate();
        UUID turn = UUID.randomUUID();
        List<String> fakeAudio = new ArrayList<>();
        AtomicInteger stops = new AtomicInteger();
        gate.begin(turn);

        assertTrue(gate.accept(turn, () -> fakeAudio.add("heard-prefix")));
        assertTrue(gate.cancel(turn, () -> {
            stops.incrementAndGet();
            fakeAudio.clear();
        }));

        assertEquals(1, stops.get());
        assertTrue(fakeAudio.isEmpty());
        assertEquals(PlaybackTurnGate.State.CANCELLED, gate.state());
        assertFalse(gate.accept(turn, () -> fakeAudio.add("late-stale-audio")));
        assertFalse(gate.cancel(turn, stops::incrementAndGet));
        assertTrue(fakeAudio.isEmpty(), "late audio must not resurrect cancelled playback");
    }

    @Test
    void producerSealFlushesTailThenRejectsAnyLateChunk() {
        var gate = new PlaybackTurnGate();
        UUID turn = UUID.randomUUID();
        List<String> fakeAudio = new ArrayList<>();
        AtomicInteger flushes = new AtomicInteger();
        gate.begin(turn);

        assertTrue(gate.accept(turn, () -> fakeAudio.add("full-frame")));
        assertTrue(gate.beginDrain(turn, () -> {
            flushes.incrementAndGet();
            fakeAudio.add("final-partial-frame");
        }));
        assertTrue(gate.beginDrain(turn, flushes::incrementAndGet), "repeat drain is idempotent");
        assertEquals(1, flushes.get());
        assertFalse(gate.accept(turn, () -> fakeAudio.add("late")));
        assertEquals(List.of("full-frame", "final-partial-frame"), fakeAudio);
        assertTrue(gate.completeDrainedTurn());
        assertEquals(PlaybackTurnGate.State.COMPLETED, gate.state());
    }

    @Test
    void staleTurnCannotAffectReplacementTurn() {
        var gate = new PlaybackTurnGate();
        UUID oldTurn = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        List<String> fakeAudio = new ArrayList<>();
        gate.begin(oldTurn);
        gate.cancel(oldTurn, fakeAudio::clear);
        gate.begin(replacement);

        assertFalse(gate.accept(oldTurn, () -> fakeAudio.add("old")));
        assertTrue(gate.accept(replacement, () -> fakeAudio.add("replacement")));
        assertFalse(gate.cancel(oldTurn, fakeAudio::clear));
        assertEquals(List.of("replacement"), fakeAudio);
    }

    @Test
    void everyPlaybackModeUsesTheSameCancellationContract() {
        for (String mode : List.of("gemini-live", "flash-tts", "pregenerated")) {
            var gate = new PlaybackTurnGate();
            UUID turn = UUID.randomUUID();
            List<String> fakeAudio = new ArrayList<>();
            gate.begin(turn);
            assertTrue(gate.accept(turn, () -> fakeAudio.add(mode)));
            assertTrue(gate.cancel(turn, fakeAudio::clear));
            assertFalse(gate.accept(turn, () -> fakeAudio.add("late-" + mode)));
            assertTrue(fakeAudio.isEmpty(), mode + " must share stop/discard semantics");
        }
    }
}
