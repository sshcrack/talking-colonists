# 09 — Audio continuity and interruption

Dependencies: 05, 06.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Compare the Errands audio workarounds against `GeminiStream`, audio providers,
pregeneration playback, paired Live conversations, and the existing Flash/TTS drain
wait. Create reproductions for duplicate goodbyes, cut-off final sentences, stale
audio after interruption, and stationary citizen-to-citizen sound. Fix remaining
defects through shared playback/session behavior rather than mode-specific mixins.

Graceful ending drains pending audible output within a bounded timeout, while
barge-in immediately cancels playback and discards queued and late audio for that
turn. Associate playback with turn identity. Update the model context to reflect
interruption using the provider's supported semantics; do not record unplayed text
as fully heard. Keep one owner for goodbye generation and completion.

Make conversation audio follow the actual speaker unless an explicit podium anchor
is configured. Verify listener dimension/range and channel cleanup. Use existing
voice-chat abstractions where possible.

## Acceptance

- Fake-audio tests show final buffers drain before graceful close, timeout is bounded,
  barge-in stops promptly, and late chunks cannot resurrect cancelled playback.
- Repeated end signals yield at most one goodbye and one completion event.
- All supported playback modes exercise equivalent interruption and cleanup behavior.
- Record in-world checks for moving speakers, podium anchoring, and audible overlap
  on both loaders; preserve reports for cases not reproducible.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Core graceful close now waits for audible output to drain within a bound instead
  of immediately stopping the stream at turn completion. Invalid-session replay
  clears queued stale audio, late output after a requested final generation is
  ignored, and repeated graceful-end state is guarded.
- Pregenerated playback now has a core-owned interruption/takeover path driven by
  player voice activity, so addons do not need reflection into `GeminiStream` or the
  private pregeneration playback map. Flash/TTS paired conversation audio refreshes
  its locational channel as participants move.
- This does **not** complete task 09. Playback is not yet uniformly keyed by explicit
  turn identity, the full barge-in/provider-context semantics are incomplete, podium
  anchoring is not exposed, fake-audio coverage for every playback mode is missing,
  and both-loader in-world overlap/location checks remain.


## Implementation record — 2026-09-07 (complete)

- Compared the pinned Colonist Errands audio workarounds at
  `f270362aca847726087213c623508ad0a65354c1` against core. The reflective addon-side
  goodbye gate, stale-queue clearing, pregenerated interruption, drain wait, and
  citizen-pair location workarounds are now represented by core-owned lifecycle
  behavior rather than parallel addon state.
- Added `PlaybackTurnGate` and made every `GeminiStream` producer supply an explicit
  playback-turn UUID. Live Gemini output, pregenerated clips, and Flash/TTS playback
  all seal the producer before their final flush; cancellation revokes the exact turn,
  discards buffered/queued samples, stops playback without blocking the server thread,
  and rejects late chunks. Old turn IDs cannot cancel or enqueue into a replacement
  turn. Voice-chat player shutdown owns encoder cleanup through its stopped callback.
- Added `PlaybackDrainCoordinator` for exactly-once graceful completion. A final tail
  is flushed before close, natural playback drain wins immediately, and a 30-second
  timeout bounds failure to drain. Repeated end requests create at most one drain/
  timeout/completion. `end_conversation` now makes the model the single farewell
  owner: say at most one farewell before the tool, then remain silent instead of
  generating a second goodbye from the tool result.
- Direct microphone input now revokes local output playback immediately before the
  audio packet is sent. Gemini Live's normal automatic VAD remains authoritative for
  provider-side interruption context; the core does not fabricate an interruption
  prompt. Provider `interrupted` events cancel the same local turn. A cancelled turn's
  output transcription is not committed to the session transcript and is never
  forwarded to a citizen peer as if it had been fully heard. Session-token recovery
  also suppresses old-provider output until the replacement setup is active.
- Removed the paired-Live hidden reply-audio buffer. A peer is prompted only after the
  current speaker's exact audible turn has drained, so an interrupted/unheard reply
  cannot later resume and two Live speakers do not overlap through speculative
  generation. The normal Live, pregenerated, and controlled-turn paths use entity
  channels that follow the actual citizen. A controlled `ControlledAudioAnchor`
  remains the explicit fixed podium/PA path, with loaded/same-dimension validation and
  configured listener distance.
- Flash/TTS multi-speaker synthesis is a deliberate spatial exception: the current
  Gemini TTS dependency returns one mixed PCM stream with sample rate only, not
  per-chunk speaker/timing identity. Core therefore keeps that mixed dialogue on the
  moving participant centroid instead of falsely emitting both voices from one
  citizen. Splitting one generated dialogue into many independent TTS requests would
  change quota, latency, and performance semantics and is not used as a fake speaker
  attribution workaround. Cleanup and interruption still use the same turn gate as
  the other playback modes.
- Added deterministic fake-audio regression coverage for final-tail flush, bounded
  graceful timeout, exactly-once repeated end handling, prompt stop/discard, stale
  late-chunk rejection, replacement-turn isolation, and the shared cancellation
  contract used by Live, Flash/TTS, and pregenerated playback.

### In-world validation record

Automated tests and the regular client smoke can verify lifecycle wiring and that both
supported clients construct and enter a world, but they cannot establish what a human
listener hears. The following acoustic checks are therefore recorded rather than
silently claimed as performed:

| Loader | Moving entity-following output | Fixed podium anchor | Barge-in/no stale resume | No audible Live-turn overlap |
| --- | --- | --- | --- | --- |
| Minecraft 1.20.1 Forge | Unverified | Unverified | Unverified | Unverified |
| Minecraft 1.21.1 NeoForge | Unverified | Unverified | Unverified | Unverified |

For the moving-speaker check, walk beside a speaking citizen and confirm the source
tracks the entity. For the podium check, walk around a controlled fixed anchor and
confirm the source stays at the podium. For interruption, speak over an active direct
turn after playback starts and confirm it stops promptly with no old tail resuming.
For paired Live overlap, remain between both citizens for several turns and confirm
the next speaker starts only after the prior audible turn has ended. Flash/TTS should
be checked separately as a mixed moving-centroid source, per the limitation above.

### Validation

- `GRADLE_USER_HOME=/cache/gradle ./gradlew test --no-daemon --max-workers=1` — passed
  for both supported loaders during implementation.
- `GRADLE_USER_HOME=/cache/gradle ./gradlew test buildAndCollect verifyApiJar compileAddonApiExamples --no-daemon --max-workers=1` — passed for both `1.20.1-forge` and `1.21.1-neoforge`, including API-jar isolation and addon example compilation.
- `GRADLE_USER_HOME=/cache/gradle CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh` — passed for both loaders. Each real graphical client automatically created and entered the smoke world so runtime mixins were exercised, emitted the healthy auto-quit marker, and closed normally. The staged launch fingerprint is `40c5c38ddfd0ea7d3c6653328a59aa96802c6c7352c2018a97821bee8a685676`.
- `git diff --check` and `git diff --cached --check` passed.
