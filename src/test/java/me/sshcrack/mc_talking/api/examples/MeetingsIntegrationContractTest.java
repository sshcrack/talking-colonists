package me.sshcrack.mc_talking.api.examples;

import me.sshcrack.mc_talking.api.conversation.ControlledTurnResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingsIntegrationContractTest {
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Test
    void arrivalCompletesBeforeFloorGrantAndSpeechRequest() {
        Harness harness = new Harness();
        CompletableFuture<MeetingTurnSequencer.SequenceResult> sequence = harness.run("Ada");

        assertEquals(List.of("move:Ada"), harness.events);
        assertFalse(sequence.isDone());

        harness.arrive("Ada");
        assertEquals(List.of("move:Ada", "floor:Ada", "turn:Ada"), harness.events);
        assertFalse(sequence.isDone());

        harness.finish("Ada", completed("Ada spoke"));
        assertEquals(MeetingTurnSequencer.SequenceResult.COMPLETED, sequence.join());
    }

    @Test
    void audibleCompletionPrecedesNextFloorGrantAcrossThreeCitizens() {
        Harness harness = new Harness();
        CompletableFuture<MeetingTurnSequencer.SequenceResult> sequence = harness.run("Ada", "Borin", "Cora");

        harness.arrive("Ada");
        assertFalse(harness.events.contains("floor:Borin"));
        harness.finish("Ada", completed("first audible line finished"));

        assertEquals("move:Borin", harness.events.get(harness.events.size() - 1));
        harness.arrive("Borin");
        assertTrue(harness.events.indexOf("finish:Ada") < harness.events.indexOf("floor:Borin"));
        harness.finish("Borin", completed("second audible line finished"));

        harness.arrive("Cora");
        assertTrue(harness.events.indexOf("finish:Borin") < harness.events.indexOf("floor:Cora"));
        harness.finish("Cora", completed("third audible line finished"));

        assertEquals(MeetingTurnSequencer.SequenceResult.COMPLETED, sequence.join());
    }

    @Test
    void nextStepContextUpdateRunsOnlyAfterPreviousAudibleTurnCompletes() {
        List<String> events = new ArrayList<>();
        Map<String, CompletableFuture<Void>> arrivals = new HashMap<>();
        Map<String, CompletableFuture<ControlledTurnResult>> turns = new HashMap<>();
        var sequencer = new MeetingTurnSequencer<String>(
                speaker -> {
                    events.add("move:" + speaker);
                    return arrivals.computeIfAbsent(speaker, ignored -> new CompletableFuture<>());
                },
                (speaker, instruction) -> {
                    events.add("turn:" + speaker);
                    return turns.computeIfAbsent(speaker, ignored -> new CompletableFuture<>());
                },
                new MeetingTurnSequencer.Observer<>() { });

        var sequence = sequencer.run(List.of(
                new MeetingTurnSequencer.Step<>("Ada", "Open", () -> { }),
                new MeetingTurnSequencer.Step<>("Borin", "Answer player", () -> events.add("player+agenda"))));

        arrivals.get("Ada").complete(null);
        assertFalse(events.contains("player+agenda"));
        turns.get("Ada").complete(completed("first audible line finished"));

        assertTrue(events.indexOf("player+agenda") > events.indexOf("turn:Ada"));
        assertTrue(events.indexOf("player+agenda") < events.indexOf("move:Borin"));
        arrivals.get("Borin").complete(null);
        turns.get("Borin").complete(completed("answer finished"));
        assertEquals(MeetingTurnSequencer.SequenceResult.COMPLETED, sequence.toCompletableFuture().join());
    }

    @Test
    void unavailableOrFailedTurnRecoversButEndedMeetingStopsFloorAdvance() {
        Harness harness = new Harness();
        CompletableFuture<MeetingTurnSequencer.SequenceResult> sequence = harness.run("Ada", "Borin", "Cora");

        harness.arrive("Ada");
        harness.finish("Ada", ControlledTurnResult.rejected(
                SESSION_ID,
                UUID.randomUUID(),
                ControlledTurnResult.FailureReason.SPEAKER_UNAVAILABLE,
                "speaker left the meeting area"));
        assertEquals(List.of("Ada"), harness.unavailable);
        assertEquals("move:Borin", harness.events.get(harness.events.size() - 1));

        harness.arrive("Borin");
        harness.finish("Borin", ControlledTurnResult.rejected(
                SESSION_ID,
                UUID.randomUUID(),
                ControlledTurnResult.FailureReason.CAPACITY_EXHAUSTED,
                "provider busy"));
        assertEquals(List.of("Borin"), harness.failed);
        assertEquals("move:Cora", harness.events.get(harness.events.size() - 1));

        harness.arrive("Cora");
        harness.finish("Cora", ControlledTurnResult.sessionEnded(
                SESSION_ID, UUID.randomUUID(), "caller ended meeting"));

        assertEquals(List.of("Cora"), harness.ended);
        assertEquals(MeetingTurnSequencer.SequenceResult.MEETING_ENDED, sequence.join());
    }

    private static ControlledTurnResult completed(String transcript) {
        return ControlledTurnResult.completed(SESSION_ID, UUID.randomUUID(), transcript);
    }

    private static final class Harness implements MeetingTurnSequencer.Observer<String> {
        private final List<String> events = new ArrayList<>();
        private final List<String> unavailable = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private final List<String> ended = new ArrayList<>();
        private final Map<String, CompletableFuture<Void>> arrivals = new HashMap<>();
        private final Map<String, CompletableFuture<ControlledTurnResult>> turns = new HashMap<>();

        CompletableFuture<MeetingTurnSequencer.SequenceResult> run(String... speakers) {
            var sequencer = new MeetingTurnSequencer<String>(
                    speaker -> {
                        events.add("move:" + speaker);
                        return arrivals.computeIfAbsent(speaker, ignored -> new CompletableFuture<>());
                    },
                    (speaker, instruction) -> {
                        events.add("turn:" + speaker);
                        return turns.computeIfAbsent(speaker, ignored -> new CompletableFuture<>());
                    },
                    this);
            List<MeetingTurnSequencer.Step<String>> steps = List.of(speakers).stream()
                    .map(speaker -> new MeetingTurnSequencer.Step<>(speaker, "Speak", () -> { }))
                    .toList();
            return sequencer.run(steps).toCompletableFuture();
        }

        void arrive(String speaker) {
            arrivals.get(speaker).complete(null);
        }

        void finish(String speaker, ControlledTurnResult result) {
            events.add("finish:" + speaker);
            turns.get(speaker).complete(result);
        }

        @Override
        public void onFloorGranted(String speaker) {
            events.add("floor:" + speaker);
        }

        @Override
        public void onSpeakerUnavailable(String speaker, ControlledTurnResult result) {
            unavailable.add(speaker);
        }

        @Override
        public void onMeetingEnded(String speaker, ControlledTurnResult result) {
            ended.add(speaker);
        }

        @Override
        public void onTurnFailed(String speaker, ControlledTurnResult result) {
            failed.add(speaker);
        }
    }
}
