# 11 — Optional autonomous group discussion

Dependencies: 06, 07, 09.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Build optional automatic turn selection on top of controlled sessions. Support a
small multi-citizen discussion with shared history and bounded turns, duration,
response length, and provider concurrency. Define fairness and prevent one speaker
from monopolizing turns or participants responding indefinitely to each other.
Use the same interruption and playback-completion semantics as controlled meetings.

Expose automatic selection as a policy a caller can opt into or pause. Meetings
must retain exclusive floor control unless it delegates that control explicitly.
Allow addons to supply family/shop/group context through prompt contributions;
core does not decide addon-specific attendance, schedules, or movement.

## Acceptance

- Three or more participants converse with correct speaker attribution and bounded
  resource use; no connection is required for every silent attendee.
- Tests cover turn limits, unavailable participants, player interruption, policy
  pause/resume, and manual floor ownership with no overlapping speakers.
- Existing two-citizen behavior remains usable and shares the lifecycle implementation.
- Document default limits and the caller's opt-in policy interface.


## Implementation record — 2026-09-07

Status: Complete.

### Public contract and behavior

- Added `AutonomousDiscussionPolicy` with bounded defaults of 8 completed turns, 2 minutes of
  scheduling time, and 256 provider output tokens per automatic turn. Construction is limited to
  1–64 turns, a positive duration up to 15 minutes, and 32–2048 output tokens.
- Added `AutonomousDiscussionHandle` with explicit running/paused/completed/stopped state, typed
  pause/completion reasons, pause/resume/stop controls, and terminal completion future.
- Added `ControlledConversationSession.delegateAutonomousDiscussion(...)`; controlled sessions keep
  manual/exclusive floor ownership unless the caller opts into this method. A paused or completed
  delegation returns the idle floor to manual `requestTurn(...)` calls.
- Automatic selection is deterministic round-robin over the caller-supplied participant order, skips
  unavailable participants before opening a provider connection, and never gives the same citizen two
  consecutive completed automatic turns. Exactly one controlled provider turn can be active.
- Player takeover pauses automatic selection instead of starting another speaker. Capacity loss also
  pauses rather than spinning. Caller pause lets the current audible turn drain, and resume waits for
  an in-progress manual turn before selecting another automatic speaker.
- Automatic turns reuse the existing controlled session/turn IDs, shared attributed transcript,
  prompt-contribution context, exact cancellation identity, player preemption, and audible-playback
  completion boundary. Silent attendees therefore hold no provider slot or Live connection.
- The automatic response ceiling is sent as Gemini Live `generationConfig.maxOutputTokens`; the local
  Gemini Live Library 2.3.6 already exposes this field. Current Google Live API documentation was
  checked and lists `generationConfig` on `BidiGenerateContentSetup` while `maxOutputTokens` is not
  among the unsupported fields. The existing 8,000-character shared-history bound remains independent.
- Existing `createPairConversation(...)` remains available. A controlled session with exactly two
  participants can also use the same bounded autonomous lifecycle, covered by regression test.
- Addons continue to own attendance, group membership, schedules, seating, navigation, and movement.
  Family/shop/group facts are supplied through the existing prompt-contributor context; core does not
  infer those concepts.

### Changed files

- `src/api/java/me/sshcrack/mc_talking/api/conversation/AutonomousDiscussionPolicy.java`
- `src/api/java/me/sshcrack/mc_talking/api/conversation/AutonomousDiscussionHandle.java`
- `src/api/java/me/sshcrack/mc_talking/api/conversation/ControlledConversationSession.java`
- `src/main/java/me/sshcrack/mc_talking/internal/api/ControlledConversationRuntime.java`
- `src/main/java/me/sshcrack/mc_talking/internal/api/TalkingColonistsApiBackend.java`
- `src/main/java/me/sshcrack/mc_talking/ConversationManager.java`
- `src/main/java/me/sshcrack/mc_talking/manager/CitizenWsClient.java`
- controlled-session contract/runtime tests and the compile-checked Colony Meetings example
- `docs/addon-api.md` and `docs/meetings-integration.md`

### Validation

- Focused controlled-session tests pass for both supported loaders, covering 3-citizen attribution and
  fairness, turn limits, provider output-token propagation, unavailable participants, player
  interruption, pause/resume, manual floor ownership with no overlap, duration limits, and two-citizen
  autonomous reuse.
- `GRADLE_USER_HOME=/cache/gradle ./gradlew test buildAndCollect verifyApiJar compileAddonApiExamples --no-daemon --max-workers=1` — passed for Minecraft 1.20.1 Forge and 1.21.1 NeoForge.
- `GRADLE_USER_HOME=/cache/gradle CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh` — passed both clients after actually entering a world; verification fingerprint `78d868b957cf7695cc2d0ebb9ce38f8712b6e6935a2ee985087da643e0cc191f`.
- Gemini Live Library 2.3.6 was published only into the shared local Maven cache for validation because
  the artifact was not yet available from the configured remote repository. No Gemini library source
  changes were made for this task.

### Remaining manual checks

No task-specific manual acceptance check remains. The existing meeting spatial-audio and interactive
barge-in checks documented in `docs/meetings-integration.md` remain general in-world validation work
from task 07/09 and were not expanded by this policy layer.
