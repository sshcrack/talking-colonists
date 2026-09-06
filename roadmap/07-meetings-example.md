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
