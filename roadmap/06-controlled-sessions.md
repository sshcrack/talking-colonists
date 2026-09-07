# 06 — Controlled conversation turns

Dependencies: 03, 04, 05.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Add a public conversation session handle for external orchestration. It must open
with participants and context, accept a requested speaker/topic, allow interruption,
and end with a reason. Hide WebSockets, capacity claims, audio channels, and busy
flags. Provide typed failures for unavailable citizens, unsupported operations,
capacity exhaustion, and closed sessions.

Support more than two participants through explicitly selected turns and a bounded
shared transcript with speaker identity. Define when participants reserve resources
and when only the current speaker consumes provider capacity. Do not open a Live
connection for every attendee. Honor per-session tool permissions and contribution
context. Define text versus audible turn completion, and marshal public event delivery
to a documented thread. Events must identify the session and turn.

Support a moving speaker or caller-selected podium audio location. Define what
happens when a player interrupts, a speaker unloads, or the caller ends mid-turn.
Use one terminal completion per turn and reject stale work from interrupted turns.
Preserve ordinary player and existing citizen conversations through the same lifecycle.

## Acceptance

- Three citizens can take controlled turns sharing correctly attributed history.
- Generation completion does not report playback completion while audio remains.
- Cancellation, unavailable speaker, capacity exhaustion, and late callbacks produce
  predictable results without leaking slots or replaying cancelled audio.
- A fake-backed addon example can use the interface without internal class access.
- Public Javadocs cover thread ownership, lifecycle, capacity, interruption, and tools.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added `ControlledConversationSession` and
  `CitizenConversationService.createControlledSession`. A caller supplies
  participants/agenda, explicitly grants one participant the floor, can add player
  statements, update the agenda, interrupt the current turn, and end the session.
  Silent attendees consume no provider slot. Shared history is bounded and attributed
  by speaker; a turn future completes from the audible ambient-session completion
  path rather than generation-complete alone.
- WebSockets, audio streams, capacity sets, and busy flags remain hidden from addons.
- This does **not** complete task 06. Typed failure reasons are still coarse,
  per-session tool permissions/contribution context are missing, there is no explicit
  podium/moving-audio anchor API, turn/session IDs and stale-late-callback tests are
  incomplete, and player-interruption/unload behavior has not yet been validated by
  the required fake-backed acceptance suite.


## Implementation record — 2026-09-07 (complete)

- Replaced the coarse controlled-turn result path with stable controlled `sessionId`/`turnId`
  identities and `ControlledTurnResult`. Non-participants, closed sessions, an already-owned floor,
  provider unavailability, unavailable/unloaded citizens, capacity exhaustion, unsupported
  operations, provider failures, interruption, and caller-ended sessions have typed terminal
  outcomes. Each turn has exactly one terminal completion; callbacks carrying an old turn identity
  cannot append transcript text, clear a replacement turn, or reopen an ended meeting.
- Added `ControlledConversationOptions` for per-session addon-tool authority. The allow-list is
  applied both when Gemini tool declarations are assembled and again at execution time. Controlled
  tool contexts carry the public controlled session ID and current turn ID; command idempotency uses
  the turn as its provider-call scope so call IDs from separate Live connections cannot collide.
  Prompt contributors receive the same immutable session/turn identity plus the agenda snapshot.
- Added `ControlledAudioAnchor`. The default path remains an entity audio channel that follows a
  moving speaker; a caller can instead select a fixed locational podium/microphone position in the
  speaker's dimension. Cross-dimension fixed anchors are explicitly unsupported rather than silently
  routing audio somewhere unrelated. Silent participants still open no provider connection and only
  the active turn consumes low-priority foreground capacity.
- Factored orchestration into `ControlledConversationRuntime` with Minecraft/provider/audio hooks.
  The public future is completed on the server execution path from the existing audible-completion
  callback, never merely on Gemini generation completion. Player preemption maps to interruption;
  caller interruption immediately cancels the owned ambient session; unload/provider failure and
  mid-turn session end all release the floor predictably. Ordinary player and pair conversation
  paths remain on the shared foreground lifecycle and were not given parallel busy/client state.
- Added deterministic fake-backed tests covering a three-citizen sequence with correctly attributed
  shared history, agenda snapshots, generation-vs-audible completion, capacity exhaustion, unloaded
  speakers, non-participants, concurrent floor requests, player preemption, caller interruption,
  session end, stale late callbacks, per-turn tool/session context, and podium-anchor forwarding. The
  compile-checked addon example uses only the public API and demonstrates failure recovery.
- `docs/addon-api.md` documents server-thread completion, capacity ownership, interruption/stale-work
  semantics, moving-speaker versus podium audio, typed failures, agenda snapshotting, and controlled
  tool permissions. The old service creation signature was removed rather than retained as a
  compatibility overload; this follows the repository's intentional breaking addon-API policy.

### Validation

- `GRADLE_USER_HOME=/cache/gradle ./gradlew test --no-daemon --max-workers=1` passed on both loaders.
- `GRADLE_USER_HOME=/cache/gradle ./gradlew buildAndCollect verifyApiJar compileAddonApiExamples --no-daemon --max-workers=1`
  passed for both `1.20.1-forge` and `1.21.1-neoforge`, including isolated API-jar verification.
- `GRADLE_USER_HOME=/cache/gradle CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh`
  passed for both loaders. Each real client constructed the mod/mixins, created and entered the
  disposable smoke world, reached `MC_TALKING_AUTOQUIT_SUCCESS:world`, and auto-closed. The documented
  metadata-only fallback skipped only Mojang cosmetic asset-object downloads.

No task-06 acceptance criteria remain.
