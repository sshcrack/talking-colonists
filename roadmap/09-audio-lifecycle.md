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
