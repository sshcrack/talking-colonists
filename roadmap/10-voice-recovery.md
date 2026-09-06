# 10 — Model-specific voice recovery

Dependencies: 01, 05.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Inspect voice selection and error propagation in the mod and Gemini Live Library.
Introduce bounded fallback after an explicit unsupported-voice response. A generic
1007 code alone is insufficient evidence: check structured errors or a narrowly
recognized voice-rejection reason. Scope learned exclusions by model and Live/TTS
mode so a rejection in one backend cannot silence the other.

Preserve the citizen's preferred deterministic voice, selecting a stable supported
fallback only when necessary. Verify fallback support per backend, limit attempts,
and produce a useful terminal error when candidates are exhausted. Make learned
exclusions bounded, expiring, and resettable; define persistence and model-change
behavior. Handle concurrent selections and corrupt stored records safely.

## Acceptance

- Tests cover explicit voice rejection, unrelated 1007, authentication/quota/network
  failure, successful fallback, exhaustion, expiry/reset, and mode/model isolation.
- Only confirmed voice rejection changes the exclusion store.
- A citizen can recover from its preferred rejected voice without changing other
  citizens' preferred voices or entering a reconnect loop.
- Diagnostics identify model/mode/voice and recovery result without credentials.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added `VoiceSelectionService`: deterministic preferred voices are preserved, but a
  narrowly recognized explicit unsupported-voice `1007` temporarily excludes that
  voice for the selected model and chooses a stable fallback. Generic 1007,
  authentication, quota, and network failures do not alter exclusions. Pregeneration
  and Live setup use the same selector. Added focused unit tests for rejection and
  model isolation.
- This does **not** complete task 10. Exclusions are currently model-scoped but not
  separately keyed by Live/TTS backend, terminal candidate exhaustion diagnostics
  and explicit reset/public lifecycle are incomplete, and the full expiry,
  concurrency, corrupt-state, auth/quota/network acceptance matrix remains.
