package me.sshcrack.mc_talking.api.examples;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import me.sshcrack.mc_talking.api.conversation.AutonomousDiscussionHandle;
import me.sshcrack.mc_talking.api.conversation.AutonomousDiscussionPolicy;
import me.sshcrack.mc_talking.api.conversation.CitizenConversationService;
import me.sshcrack.mc_talking.api.conversation.ControlledAudioAnchor;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationOptions;
import me.sshcrack.mc_talking.api.conversation.ControlledConversationSession;
import me.sshcrack.mc_talking.api.conversation.ControlledTurnResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Compile-checked Colony Meetings-style integration using only the supported addon API. */
final class ColonyMeetingsExample {
    private ColonyMeetingsExample() {
    }

    /** Caller-owned navigation. Complete the stage only after the citizen has actually arrived. */
    @FunctionalInterface
    interface MovementController {
        CompletionStage<Void> moveToPodiumAndWaitForArrival(
                AbstractEntityCitizen citizen,
                Vec3 podiumPosition
        );
    }

    /** Caller-owned UI/floor policy hooks. Seating, blocks and hand raising stay outside core. */
    interface FloorCallbacks extends MeetingTurnSequencer.Observer<AbstractEntityCitizen> {
    }

    record RunningMeeting(
            ControlledConversationSession session,
            CompletionStage<MeetingTurnSequencer.SequenceResult> completion
    ) {
    }

    static RunningMeeting startThreeCitizenMeeting(
            MinecraftServer server,
            List<AbstractEntityCitizen> attendees,
            ServerPlayer askingPlayer,
            String agenda,
            Vec3 podiumPosition,
            MovementController movement,
            FloorCallbacks callbacks
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(attendees, "attendees");
        Objects.requireNonNull(askingPlayer, "askingPlayer");
        Objects.requireNonNull(agenda, "agenda");
        Objects.requireNonNull(podiumPosition, "podiumPosition");
        Objects.requireNonNull(movement, "movement");
        Objects.requireNonNull(callbacks, "callbacks");
        if (attendees.size() < 3) {
            throw new IllegalArgumentException("the example requires at least three attendees");
        }

        ControlledConversationSession meeting = CitizenConversationService.createControlledSession(
                server,
                attendees,
                agenda,
                ControlledConversationOptions.allowAddonTools(Set.of("meetings:record_vote")));

        AbstractEntityCitizen first = attendees.get(0);
        AbstractEntityCitizen second = attendees.get(1);
        AbstractEntityCitizen third = attendees.get(2);
        String followUpAgenda = agenda + "; answer the player's question and agree on next actions";

        MeetingTurnSequencer<AbstractEntityCitizen> sequencer = new MeetingTurnSequencer<>(
                citizen -> movement.moveToPodiumAndWaitForArrival(citizen, podiumPosition),
                (citizen, instruction) -> {
                    // Arrival has completed before this callback runs. Constructing the fixed audio
                    // anchor now also uses the speaker's actual current dimension at the podium.
                    ControlledAudioAnchor podium = ControlledAudioAnchor.at(
                            citizen.level().dimension(), podiumPosition);
                    return meeting.requestTurn(citizen, instruction, podium);
                },
                callbacks);

        List<MeetingTurnSequencer.Step<AbstractEntityCitizen>> floor = List.of(
                new MeetingTurnSequencer.Step<>(
                        first,
                        "Open the meeting with your view on the current agenda.",
                        () -> { }),
                new MeetingTurnSequencer.Step<>(
                        second,
                        "Answer the player's question, using the updated agenda and shared transcript.",
                        () -> {
                            // The first citizen's audible turn is terminal before this step begins.
                            meeting.addPlayerStatement(askingPlayer, "What should we improve first?");
                            meeting.setAgenda(followUpAgenda);
                        }),
                new MeetingTurnSequencer.Step<>(
                        third,
                        "Give the final citizen recommendation and react to the earlier speakers.",
                        () -> { }));

        CompletionStage<MeetingTurnSequencer.SequenceResult> completion = sequencer.run(floor)
                .thenApply(result -> {
                    if (result == MeetingTurnSequencer.SequenceResult.COMPLETED) {
                        meeting.end(ControlledConversationSession.EndReason.COMPLETED);
                    }
                    return result;
                });
        return new RunningMeeting(meeting, completion);
    }

    /**
     * Optional delegation: normal meetings keep manual floor ownership unless the addon calls this.
     * The returned handle can be paused to regain manual floor control without ending the meeting.
     */
    static AutonomousDiscussionHandle delegateBoundedDiscussion(ControlledConversationSession meeting) {
        return meeting.delegateAutonomousDiscussion(AutonomousDiscussionPolicy.defaults());
    }

    /** Demonstrates player/caller barge-in. The current turn ends; the meeting itself stays open. */
    static boolean interruptCurrentSpeaker(RunningMeeting meeting) {
        return meeting.session().interruptTurn();
    }

    /** Demonstrates caller-owned early cleanup, for example when the meeting block is removed. */
    static void cancelMeeting(RunningMeeting meeting) {
        meeting.session().end(ControlledConversationSession.EndReason.CALLER_CANCELLED);
    }

    /** Minimal typed failure split a meetings addon can use for its own floor-state/UI recovery. */
    static String failureBucket(ControlledTurnResult result) {
        if (result.status() == ControlledTurnResult.Status.SESSION_ENDED
                || result.failureReason() == ControlledTurnResult.FailureReason.SESSION_CLOSED) {
            return "meeting-ended";
        }
        if (result.failureReason() == ControlledTurnResult.FailureReason.SPEAKER_UNAVAILABLE
                || result.failureReason() == ControlledTurnResult.FailureReason.SPEAKER_UNLOADED) {
            return "speaker-unavailable";
        }
        return result.completed() ? "completed" : "recoverable-turn-failure";
    }
}
