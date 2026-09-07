# Gemini transport, voice, and audio reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Review Gemini transport/model behavior and the complete audio lifecycle. Primary areas
include `manager/Gemini*`, `conversations/LiveConversationWsClient`,
`conversations/TtsVoiceRecovery`, `manager/audio/`, `internal/audio/`, pre-generation
playback, voice selection, quota handling, and voice-chat integration.

## Invariants to stress

- Websocket/provider close, error, reconnect, and cancellation paths are bounded and
  idempotent. A late provider callback cannot emit into or revive a closed/replaced
  conversation.
- Native/audio resources such as encoders, channels, streams, queues, and playback
  tasks have exactly one effective owner and are released on success, error,
  cancellation, disconnect, and partial initialization.
- Audio ordering matches turn ordering. Drain/interrupt gates cannot drop the final
  chunk, play chunks from a previous turn/session, or deadlock while waiting on work
  that needs the same thread/state to finish.
- Null/unavailable voice-chat channels and disconnects produce controlled failure, not
  NPEs or leaked sessions.
- Voice/model selection uses capabilities actually supported by the selected Gemini
  model. Recovery from an unsupported voice/model is bounded and does not mutate
  global/citizen state incorrectly.
- Live API behavior is not inferred from TTS-only behavior. When a changed request,
  model name, voice, or error code depends on the Gemini Live Library, inspect the
  local library contract and note any assumption that still needs official-doc
  verification.
- Quota accounting and retries charge/restore the correct operation and cannot be
  bypassed or double-counted by recovery/retry paths.
- Audio callbacks crossing threads do not mutate Minecraft/server-owned state without
  the required handoff.

Trace teardown from both directions: provider ends first and Minecraft/session ends
first. Many lifecycle defects only appear in the second ordering.
