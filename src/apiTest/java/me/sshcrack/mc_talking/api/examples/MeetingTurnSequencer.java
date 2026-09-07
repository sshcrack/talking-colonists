package me.sshcrack.mc_talking.api.examples;

import me.sshcrack.mc_talking.api.conversation.ControlledTurnResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Addon-side floor sequencing helper used by {@link ColonyMeetingsExample}.
 *
 * <p>This deliberately lives with the compile-checked examples rather than the Talking Colonists
 * runtime. Navigation and floor policy belong to the meetings addon; core only owns each requested
 * audible turn.</p>
 */
final class MeetingTurnSequencer<S> {
    enum SequenceResult { COMPLETED, MEETING_ENDED }

    record Step<S>(S speaker, String instruction, Runnable beforeArrival) {
        Step {
            Objects.requireNonNull(speaker, "speaker");
            Objects.requireNonNull(instruction, "instruction");
            Objects.requireNonNull(beforeArrival, "beforeArrival");
        }
    }

    @FunctionalInterface
    interface ArrivalGate<S> {
        CompletionStage<Void> moveAndWaitForArrival(S speaker);
    }

    @FunctionalInterface
    interface TurnRequester<S> {
        CompletionStage<ControlledTurnResult> request(S speaker, String instruction);
    }

    interface Observer<S> {
        default void onFloorGranted(S speaker) { }
        default void onSpeakerUnavailable(S speaker, ControlledTurnResult result) { }
        default void onMeetingEnded(S speaker, ControlledTurnResult result) { }
        default void onTurnFailed(S speaker, ControlledTurnResult result) { }
        default void onMovementFailed(S speaker, Throwable failure) { }
        default void onTurnException(S speaker, Throwable failure) { }
    }

    private final ArrivalGate<S> arrivalGate;
    private final TurnRequester<S> turnRequester;
    private final Observer<S> observer;

    MeetingTurnSequencer(
            ArrivalGate<S> arrivalGate,
            TurnRequester<S> turnRequester,
            Observer<S> observer
    ) {
        this.arrivalGate = Objects.requireNonNull(arrivalGate, "arrivalGate");
        this.turnRequester = Objects.requireNonNull(turnRequester, "turnRequester");
        this.observer = Objects.requireNonNull(observer, "observer");
    }

    CompletionStage<SequenceResult> run(List<Step<S>> steps) {
        return runNext(List.copyOf(steps), 0);
    }

    private CompletionStage<SequenceResult> runNext(List<Step<S>> steps, int index) {
        if (index >= steps.size()) {
            return CompletableFuture.completedFuture(SequenceResult.COMPLETED);
        }

        Step<S> step = steps.get(index);
        try {
            step.beforeArrival().run();
        } catch (Throwable failure) {
            observer.onMovementFailed(step.speaker(), failure);
            return runNext(steps, index + 1);
        }

        final CompletionStage<Void> arrival;
        try {
            arrival = Objects.requireNonNull(
                    arrivalGate.moveAndWaitForArrival(step.speaker()),
                    "arrival stage");
        } catch (Throwable failure) {
            observer.onMovementFailed(step.speaker(), failure);
            return runNext(steps, index + 1);
        }

        return arrival.handle((ignored, failure) -> failure)
                .thenCompose(failure -> {
                    if (failure != null) {
                        observer.onMovementFailed(step.speaker(), failure);
                        return runNext(steps, index + 1);
                    }
                    return requestTurn(steps, index, step);
                });
    }

    private CompletionStage<SequenceResult> requestTurn(
            List<Step<S>> steps,
            int index,
            Step<S> step
    ) {
        observer.onFloorGranted(step.speaker());

        final CompletionStage<ControlledTurnResult> turn;
        try {
            turn = Objects.requireNonNull(
                    turnRequester.request(step.speaker(), step.instruction()),
                    "turn stage");
        } catch (Throwable failure) {
            observer.onTurnException(step.speaker(), failure);
            return runNext(steps, index + 1);
        }

        return turn.handle(TurnCompletion::new)
                .thenCompose(completion -> {
                    if (completion.failure() != null) {
                        observer.onTurnException(step.speaker(), completion.failure());
                        return runNext(steps, index + 1);
                    }

                    ControlledTurnResult result = completion.result();
                    if (isEndedMeeting(result)) {
                        observer.onMeetingEnded(step.speaker(), result);
                        return CompletableFuture.completedFuture(SequenceResult.MEETING_ENDED);
                    }
                    if (isUnavailableSpeaker(result)) {
                        observer.onSpeakerUnavailable(step.speaker(), result);
                    } else if (!result.completed()) {
                        observer.onTurnFailed(step.speaker(), result);
                    }
                    return runNext(steps, index + 1);
                });
    }

    private static boolean isUnavailableSpeaker(ControlledTurnResult result) {
        return result.failureReason() == ControlledTurnResult.FailureReason.SPEAKER_UNAVAILABLE
                || result.failureReason() == ControlledTurnResult.FailureReason.SPEAKER_UNLOADED;
    }

    private static boolean isEndedMeeting(ControlledTurnResult result) {
        return result.status() == ControlledTurnResult.Status.SESSION_ENDED
                || result.failureReason() == ControlledTurnResult.FailureReason.SESSION_CLOSED;
    }

    private record TurnCompletion(ControlledTurnResult result, Throwable failure) {
    }
}
