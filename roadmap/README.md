# Reliable conversations and addon integration roadmap

This roadmap implements the recommendations arising from Colonist Errands and
Colony Meetings feedback. It is an implementation queue, not a record of shipped
features. All tasks start pending. Complete one task by giving an agent its prompt
file; each prompt requires the shared execution instructions below. A `Partial`
status means a cross-cutting change implemented part of the task, but one or more
acceptance criteria still remain and are listed in that task's implementation record.

## Scope

Talking Colonists owns dialogue generation, audio playback, session lifecycle,
conversation memory, factual base-game context, and supported addon interfaces.
Colonist Errands owns errands, couriers, job switching, defense formations, promise
fulfillment rules/rewards, and its gameplay-specific relationships. Colony Meetings
owns attendance, seating, movement, hand raising, podiums, and granting the floor.
Voyager owns expedition state. Core exposes the seams these addons need.

## Implementation queue

| Order | Task and agent prompt | Depends on | Status |
| --- | --- | --- | --- |
| 01 | [Correct deterministic voice selection](01-voice-correction.md) | — | Complete |
| 02 | [Reliable structured memory generation](02-memory-reliability.md) | — | Complete |
| 03 | [Composable prompt contributions](03-prompt-contributions.md) | — | Complete |
| 04 | [Public tools and authorized execution](04-tool-interface.md) | — | Complete |
| 05 | [Session ownership and bounded recovery](05-session-lifecycle.md) | — | Complete |
| 06 | [Controlled conversation turns](06-controlled-sessions.md) | 03, 04, 05 | Partial |
| 07 | [Meetings example and integration contract](07-meetings-example.md) | 06 | Partial |
| 08 | [Verified facts and memory provenance](08-facts-and-memory.md) | 02, 03, 04 | Partial |
| 09 | [Audio continuity and interruption](09-audio-lifecycle.md) | 05, 06 | Partial |
| 10 | [Model-specific voice recovery](10-voice-recovery.md) | 01, 05 | Partial |
| 11 | [Optional autonomous group discussion](11-group-discussion.md) | 06, 07, 09 | Pending |
| 12 | [Addon migration and release verification](12-addon-migration.md) | 01–11 | Partial |

Land the small reliability fixes first. Deliver the controlled-turn integration
before autonomous group discussion. Tasks without dependencies can be implemented
independently, but changes touching the same files should be integrated sequentially.
This document does not require spawning agents or running simultaneous builds.

## Shared execution instructions

When implementing any linked task:

1. Read the repository's `AGENTS.md`, this file, the task prompt, and applicable
   nested instructions. Inspect the current worktree and preserve unrelated edits.
   Recheck the observations below against the code; completed work may supersede them.
2. Verify dependency tasks are implemented, not merely marked complete. If a required
   interface is missing, report the exact missing dependency rather than inventing
   a second implementation. Proposed type names in prompts are illustrative.
3. Implement the task through the smallest useful public interface. Keep transport,
   mutable registries, busy flags, and resource bookkeeping internal. Document thread
   ownership, failure results, cancellation, and compatibility for public additions.
4. Read `scripts/MINECOLONIES_DOCS.md` before using MineColonies APIs; verify uncertain
   behavior in the matching sources jars for both supported versions. For Gemini
   request fields, voice support, and error semantics, inspect the local library and
   verify current official Google documentation. Never infer Live support from TTS
   documentation alone.
5. Add deterministic behavioral regression tests where required by the task. There
   was no root `src/test` directory at planning time: inspect Gradle wiring and add
   the smallest runnable test setup needed. Exercise public behavior with fake
   transport/audio and controlled executors; avoid timing-dependent sleeps and tests
   that require Gemini credentials. Record exact commands and results.
6. Run relevant tests and `./gradlew buildAndCollect --no-daemon` for implementation
   changes. Verify both `1.21.1-neoforge` and `1.20.1-forge` artifacts. For any
   launch-relevant runtime/API/resource/build change, run
   `bash scripts/test-client-smoke.sh` and follow `AGENTS.md` for the verification
   marker. Never fabricate a marker or stage `.sc_active_version`.
   Update `en_us.json` when configuration values change.
7. If modifying `../gemini-live-library`, inspect its own instructions and tests,
   report changes in both repositories, and distinguish composite-build validation
   from validation against a published dependency. Prepare dependency/release notes;
   publishing, tagging, pushing, or contacting addon authors is a separate action.
8. Update this table and append a concise implementation record to the task file:
   changed interfaces/files, test evidence, remaining manual checks, and any deviations.
   Mark complete only when acceptance criteria pass. Distinguish implemented but
   unverified work from completion. Finish with a self-contained handoff.

## Planning evidence

Observed in the checkout on 2026-09-06:

- `config/AvailableAI.java`: both model voice lists contain `Archid`; the selection
  hashes citizen UUIDs into a fixed list.
- `conversations/memory/CitizenMemoryGenerator.java` and
  `PlayerConversationMemoryGenerator.java`: raw Flash responses go straight to Gson;
  parse failures are logged and abandoned. The citizen generator has an unsynchronized
  two-field save handoff.
- `api/prompt/CitizenPromptService.java`: a single replaceable global provider.
- `manager/tools/AITools.java`: private maps and built-in registration; `FunctionAction`
  execution receives citizen, colony, and parameters, without explicit actor context.
- `ConversationManager.java`: callers can manipulate slots, busy state, and clients.
- `conversations/CitizenConversation.java`: live conversation chooses participants
  zero and one; Flash/TTS already contains a playback drain wait.
- `api/prompt/view/CitizenPromptView.java`: substantial facts already exist, including
  health, housing, family, player relations, requests, and worker state.

Java paths above are relative to `src/main/java/me/sshcrack/mc_talking/`.

The [Colonist Errands repository](https://github.com/Lovkar-Squid/colonist-errands)
describes additional audio fixes and a reconnect watchdog. These are reports to
reproduce and compare, not proof that every defect remains in this checkout. Consult
specific source files and pin the reviewed commit when assessing its workarounds.
The Meetings request is a proposed integration, not an available implementation.

Google references: [structured output](https://ai.google.dev/gemini-api/docs/structured-output),
[TTS voices](https://ai.google.dev/gemini-api/docs/speech-generation),
[Live API](https://ai.google.dev/gemini-api/docs/live).
