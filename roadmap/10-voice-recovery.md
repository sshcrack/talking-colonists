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

## Implementation record — 2026-09-07 (complete)

- Reworked `VoiceSelectionService` around explicit `(backend, model, voice)` keys. Live and
  TTS exclusions can no longer poison each other, deterministic UUID-to-preferred-voice
  selection remains unchanged, fallback order is stable, exclusions expire after six hours,
  shutdown/reset clears the in-memory store, unknown/corrupt evidence is ignored, and candidate
  exhaustion raises a diagnostic containing backend, model, preferred voice, and candidate count.
- Live sessions only learn from narrowly recognized voice-specific `1007` reasons. Ordinary
  foreground sessions terminate cleanly when every candidate is exhausted instead of entering a
  reconnect loop. Live pregeneration now retries the same job off the WebSocket callback thread,
  clears partial audio before retry, has a three-attempt voice-recovery cap, and owns its cleanup
  callback exactly once. Authentication, quota, generic `1007`, and transport failures do not
  mutate voice exclusions.
- Multi-speaker TTS now uses the same deterministic selector under the TTS backend key and retries
  at most three confirmed rejected voices. A `400` response must contain structured provider JSON
  whose error message identifies exactly one configured voice and a voice-specific rejection;
  ambiguous multi-speaker errors and non-voice failures are surfaced unchanged.
- Gemini Live Library `2.3.6` preserves non-2xx TTS HTTP status and response body on
  `UnexpectedResponseException`, enabling evidence-based TTS recovery without parsing exception
  prose. Talking Colonists now requires `deps.gemini_live_lib_version=2.3.6`;
  `settings.gradle.kts` derives composite substitution coordinates dynamically from that property,
  so the version is defined only once. Publishing/tagging the library remains a separate release
  action.
- Google documentation checked on 2026-09-07 confirms Live voice selection is configured through
  `speechConfig.voiceConfig.prebuiltVoiceConfig` and that GenerateContent TTS has a separate voice
  configuration/voice set. The recovery cache is intentionally process-memory only, so no persisted
  recovery record can become stale across model/library upgrades.
- Regression coverage now includes confirmed Live/TTS rejection, unrelated 1007, auth/quota/service
  and network failures, backend/model isolation, stable fallback, retry exhaustion, expiry/reset,
  invalid evidence, concurrent learning/selection, deterministic preferred-voice preservation, and
  ambiguous multi-speaker TTS errors.
- Validation: Gemini Live Library `./gradlew test --no-daemon --max-workers=1` passed for both
  supported loaders. Talking Colonists passed composite `./gradlew test buildAndCollect --no-daemon
  --max-workers=1` against the patched 2.3.6 library for Forge 1.20.1 and NeoForge 1.21.1. The
  required real-client smoke verification is recorded by `.client-smoke-verified` for the final
  staged patch. Validation against a published 2.3.6 artifact must happen after that library release.
