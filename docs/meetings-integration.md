# Controlled meetings integration

Talking Colonists exposes controlled conversation sessions so a meetings addon can own the meeting
without owning Gemini, audio playback, provider slots, or conversation internals. The addon remains
responsible for attendance, pathing, seating, podium blocks, hand raising, agenda/floor policy, and
its own UI. Talking Colonists owns one requested citizen turn at a time and reports its terminal
result only after audible playback ends.

The compile-checked reference implementation is
`src/apiTest/java/me/sshcrack/mc_talking/api/examples/ColonyMeetingsExample.java`. It is compiled
against the stripped developer API jar for both supported loaders, so copying the integration pattern
does not require `ConversationManager`, websocket clients, audio queues, mixins, or other internal
classes.

## Minimal sequence

Open one controlled session with every citizen who may take the floor. Silent attendees do not open
provider connections. Supply the meeting agenda and only the addon tools that this meeting is allowed
to use:

```java
var meeting = CitizenConversationService.createControlledSession(
        server,
        attendees,
        "Food supply and town defenses",
        ControlledConversationOptions.allowAddonTools(Set.of("meetings:record_vote")));
```

For each selected speaker, the meetings addon first starts its own navigation and waits for its real
arrival signal. Only then grant the Talking Colonists turn:

```java
moveToPodiumAndWaitForArrival(alice).thenCompose(ignored -> {
    var podium = ControlledAudioAnchor.at(alice.level().dimension(), podiumCenter);
    return meeting.requestTurn(alice, "Give your view on food supply", podium);
}).thenAccept(result -> {
    if (result.completed()) {
        grantNextSpeaker();
    } else {
        recoverFloor(result);
    }
});
```

Do not grant the next floor from a generation callback, timer, animation, or guessed clip length. The
`requestTurn` future is the sequencing boundary: it completes on the Minecraft server thread only
when that turn reaches terminal **audible** state. A failed/rejected turn is terminal too, so external
floor state must always either advance, retry by policy, or end the meeting instead of remaining in a
"waiting for speech" state.

The reference example runs a three-citizen sequence. Before the second citizen is moved to the
podium it records a player question and changes the agenda:

```java
meeting.addPlayerStatement(player, "What should we improve first?");
meeting.setAgenda("Food supply, then answer the player and agree on next actions");
```

The second citizen's requested turn therefore sees the player statement in shared history and the new
agenda snapshot. The third citizen receives the accumulated attributed transcript. `setAgenda`
changes subsequent turns only; it cannot rewrite a prompt that has already been requested.

## Failure handling

Treat turn results as terminal state transitions, not as booleans that can leave the meeting locked:

- `SPEAKER_UNAVAILABLE` or `SPEAKER_UNLOADED`: release that caller-owned floor grant and skip/retry
  the citizen according to meeting policy. The controlled session remains open.
- `CAPACITY_EXHAUSTED`, `PROVIDER_UNAVAILABLE`, `PROVIDER_FAILED`, or another failed/rejected turn:
  clear the external waiting state and retry later, skip the speaker, or end by policy. No provider
  slot remains owned by the failed turn.
- `SESSION_ENDED` or `SESSION_CLOSED`: stop the sequence. Do not navigate or grant another speaker.
- `INTERRUPTED`: the current turn is over and stale audio/provider callbacks for it are ignored. The
  meeting remains open unless the caller also ends it.

`ControlledTurnResult.sessionId()` and `turnId()` are stable identities for matching UI/floor work to
the exact session and turn. Do not use citizen names as cancellation identities.

For caller/player barge-in, call `meeting.interruptTurn()`. For block removal, meeting cancellation,
or other early teardown, call `meeting.end(CALLER_CANCELLED)`. Normal completion should call
`meeting.end(COMPLETED)`. Ending is idempotent and cancels an active turn.

## Podium audio and microphone routing

`ControlledAudioAnchor` controls **citizen output audio**, not microphone input:

- No anchor: Talking Colonists uses the citizen entity audio channel, so the voice follows the moving
  citizen.
- Fixed anchor: Talking Colonists uses locational output at the supplied position, suitable for a
  podium or PA/microphone block. Build the anchor after arrival so its dimension matches the actual
  speaker. Cross-dimension fixed anchors are rejected. Listener range still follows Talking Colonists'
  citizen-voice distance setting.

Controlled meetings do **not** currently attach a player's Simple Voice Chat microphone stream to the
controlled session. `addPlayerStatement(...)` accepts text the caller already knows; it is not a
speech-to-text API. Talking Colonists' microphone packet routing belongs to a direct player/citizen
conversation created with `startPlayerConversation(...)`. Starting that direct conversation while a
citizen owns a controlled turn preempts and interrupts that turn; it does not automatically copy the
player's spoken question back into the controlled meeting transcript.

Therefore the supported meeting pattern today is: use caller-provided/text UI questions with
`addPlayerStatement`, or deliberately leave/interrupt controlled floor mode before starting a normal
direct microphone conversation. Native multi-party meeting microphone ingestion, speaker diarization,
and automatic reinsertion of direct-player speech into meeting history are not part of the current
controlled-session contract.

## Caller-owned callbacks

A meetings addon should keep these outside Talking Colonists and invoke the conversation API only at
the semantic boundaries:

- navigation + an actual arrival/path completion signal;
- seat/podium assignment and block validity;
- hand raising, speaker selection and floor UI;
- handling a movement failure before requesting speech;
- deciding whether an unavailable/failed speaker is skipped or retried;
- ending the session when the meeting object or world state is no longer valid.

The reference `MeetingTurnSequencer` is intentionally example-side code. It proves the ordering
contract without making Talking Colonists a meeting/navigation framework.

## Optional automatic floor delegation

Meetings keep exclusive manual floor control unless they explicitly call
`meeting.delegateAutonomousDiscussion(...)`. The compile-checked example includes a bounded
`AutonomousDiscussionPolicy.defaults()` delegation helper. Automatic discussion uses the same
controlled session and attributed transcript; it does not move attendees or choose who belongs in
the meeting. Pause the returned handle before returning to caller-selected floor grants. A current
audible automatic turn is allowed to finish, after which the paused session accepts manual
`requestTurn(...)` calls. See `docs/addon-api.md` for policy limits, fairness, provider concurrency,
player-preemption behavior, and completion reasons.

## In-world validation record

Automated tests cover arrival-before-request, audible-completion-before-next-floor, three-citizen
progression, and recovery from unavailable/capacity/ended results. The regular client smoke test also
verifies that both supported mod clients construct and enter a world, but it cannot verify what a
human listener hears or physically perform meeting interactions.

The following manual checks remain **unverified** until performed interactively on each loader:

| Loader | Fixed podium spatial audio | Player interruption/barge-in | Orderly meeting cleanup |
| --- | --- | --- | --- |
| Minecraft 1.20.1 Forge | Unverified | Unverified | Unverified |
| Minecraft 1.21.1 NeoForge | Unverified | Unverified | Unverified |

For the spatial check, walk around a fixed podium while three citizens speak and confirm output stays
at the podium. For interruption, start a controlled citizen turn, trigger the chosen caller/player
barge-in path, and confirm the current turn terminates once without later audio resuming. For cleanup,
end/remove the meeting during both idle and active states and confirm no further floor turn starts.
