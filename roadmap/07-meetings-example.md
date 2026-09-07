# 07 — Meetings example and integration contract

Dependency: 06.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Create a small compile-checked integration example or development harness showing
how Colony Meetings uses the public interface. It supplies attendees, agenda, and
podium position, opens a session, and requests a turn only after the selected citizen
has arrived. Advance the floor on audible completion. Demonstrate interruption and
ending the meeting. Keep seating, navigation, blocks, and hand raising as caller
callbacks; building a second Meetings mod is outside this task.

Include a player question followed by a citizen response and a three-citizen sequence.
Show how the caller distinguishes an unavailable speaker from an ended meeting and
how agenda updates reach subsequent turns. Document supported microphone routing
and any initial limitations explicitly.

## Acceptance

- Example compiles for both loaders and uses only supported public addon interfaces.
- Tests prove movement/arrival precedes speech and playback completion precedes the
  next floor grant; a failed turn cannot leave the meeting permanently waiting.
- Record manual in-world checks for spatial audio, player interruption, and orderly
  cleanup on both versions. Report unavailable environments as unverified.
- Publish usage docs in the repository, including a minimal sequence and failure handling.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added a compile-checked addon example showing a caller creating a controlled
  meeting, owning navigation/arrival before `requestTurn`, adding a player question,
  and requesting a subsequent citizen response. `docs/addon-api.md` documents the
  same ownership split for a Colony Meetings-style addon.
- This does **not** complete task 07. The example does not yet demonstrate podium
  anchoring, a full three-citizen floor sequence with failure recovery, or explicit
  microphone routing; behavioral arrival/playback ordering tests and both-loader
  in-world validation records remain.

## Implementation record — 2026-09-07 (complete)

- Replaced the earlier minimal meeting snippet with a dedicated compile-checked
  `ColonyMeetingsExample` and addon-side `MeetingTurnSequencer`. The example supplies attendees,
  agenda, and podium position; waits for a caller-owned movement/arrival stage before requesting a
  turn; records a player question; updates the agenda for later turns; runs a three-citizen floor
  sequence; demonstrates interruption and caller cancellation; and ends a normally completed
  meeting. Seating, navigation, podium/block validity, hand raising, and floor UI remain caller
  responsibilities.
- The sequencing helper advances only from terminal `ControlledTurnResult` futures, so audible
  completion gates the next floor grant. Unavailable/unloaded speakers are distinguished from an
  ended/closed meeting, while other rejected/failed turns release caller waiting state and continue
  according to addon policy. Movement or request exceptions are also terminal to that example step
  rather than leaving the floor permanently waiting.
- Added deterministic `MeetingsIntegrationContractTest` coverage for arrival-before-floor/speech,
  audible-completion-before-next-floor across three citizens, player/agenda context updates between
  turns, recoverable unavailable/capacity failures, and stopping on an ended meeting. `src/apiTest`
  is also compiled in the normal test source set so the same example-side sequencer is exercised,
  while `compileAddonApiExamples` continues compiling the example separately against only the
  stripped developer API jar.
- Added `docs/meetings-integration.md` and linked it from `docs/addon-api.md`. The guide documents the
  minimal sequence, typed failure handling, output anchoring, interruption/cleanup, and the current
  microphone contract. `ControlledAudioAnchor` is citizen **output** spatialization; controlled
  sessions do not ingest player Simple Voice Chat microphone packets. A normal direct player/citizen
  conversation owns current microphone routing and preempts an active controlled turn rather than
  automatically inserting spoken player audio into meeting history.
- Human in-world checks for fixed podium spatial audio, player barge-in, and orderly cleanup are
  explicitly recorded as **Unverified** for both supported loaders because the available automated
  client environment cannot evaluate what a listener hears or perform interactive meeting actions.
  This is the acceptance-prescribed unavailable-environment outcome, not an automated-pass claim.

### Validation

- `GRADLE_USER_HOME=/cache/gradle ./gradlew test compileAddonApiExamples --no-daemon --max-workers=1`
  passed for both `1.20.1-forge` and `1.21.1-neoforge`.
- `GRADLE_USER_HOME=/cache/gradle ./gradlew buildAndCollect verifyApiJar --no-daemon --max-workers=1`
  passed for both loaders, including isolated addon API jar/example verification and collected
  artifacts.
- `GRADLE_USER_HOME=/cache/gradle CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh`
  passed for both loaders. Each client reached the real in-world auto-quit success path; the
  metadata-only fallback skipped only cosmetic Mojang asset-object downloads.

No implementation acceptance criteria remain for task 07.
