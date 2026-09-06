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
