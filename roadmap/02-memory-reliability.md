# 02 — Reliable structured memory generation

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Inspect both memory generators, `GsonMemoryResponse`, memory compaction, and the
local library's `GeminiFlash` request models. Add opt-in JSON MIME/schema support
using the actual GenerateContent wire format and supported model capabilities.
Existing text callers must retain text behavior. Inspect every memory generation
path and apply structured output where its expected result is JSON; compaction
must retain its intended format.

Create one memory response parser/validator. Accept plain JSON and a single enclosing
Markdown fence, including optional `json` label and whitespace. Reject prose with
embedded JSON, truncated payloads, invalid required fields, unknown relationship
types, and invalid numeric changes according to an explicit validation policy.
Define which missing optional collections normalize to empty. Do not partially
mutate persistent memory before validation succeeds.

Replace the citizen generator's `shouldSaveMemory`/`savedResponse` handoff with a
future or synchronized state transition. Saving must happen once on the server
thread when generation and save authorization are both complete. Repeated save
requests, cancellation, failure, and shutdown must have defined outcomes. Ensure
success is logged after persistence succeeds, not simply after scheduling it.

Use bounded recovery for invalid model output if appropriate; retain an actionable
failure result without logging credentials or full private transcripts by default.

## Acceptance

- Test plain/fenced JSON, whitespace, null root, malformed/truncated output, missing
  fields, and invalid relationships; existing memories survive rejected output.
- Deterministically test generation-first, save-first, simultaneous completion,
  repeated save, cancellation, and failure. Each accepted result persists once.
- Verify request serialization includes structured-output settings only when opted in.
- Test both citizen/player paths and document the compaction format decision.
- Library and mod tests pass; both loaders build. Record the published dependency
  version requirement if the composite build contains necessary library changes.

## Implementation record — 2026-09-06

- Added opt-in structured-output support to `GeminiFlash` in Gemini Live Library
  **2.3.5**. JSON callers can now supply `generationConfig.responseMimeType =
  "application/json"` plus `responseJsonSchema`; existing text-only overloads omit
  `generationConfig` and retain their previous behavior. Request-serialization tests
  cover both modes on Forge 1.20.1 and NeoForge 1.21.1. Talking Colonists therefore
  requires Gemini Live Library 2.3.5 once these library changes are published.
- Added `MemoryStructuredOutput`, with contextual schemas for both citizen↔citizen
  and player↔citizen extraction. Citizen names and relationship targets are limited
  to actual conversation participants, relationship dimensions are limited to the
  public enum, unknown properties are rejected, and relationship deltas are bounded
  to `[-1, 1]`. Both JSON-generating memory paths opt into this schema.
- Kept memory compaction intentionally **plain text**. Both Flash compaction and the
  Live compaction client produce a human-readable summary string that is stored as
  summarized memory; it is not a machine-consumed JSON document. The Flash path
  therefore continues using the text-only `GeminiFlash` overload, whose serialization
  test verifies that structured-output settings are absent.
- Consolidated persistent-memory parsing through `MemoryResponseParser`. It accepts
  plain JSON or exactly one enclosing Markdown fence (labeled `json` or unlabeled),
  tolerates surrounding whitespace, normalizes omitted optional relationship/fact/event
  collections to empty lists, and rejects null/malformed/truncated roots, missing
  required fields, embedded prose, explicit-null collections, unknown citizens or
  relationship targets/types, duplicate citizens, and non-finite/out-of-range deltas
  before any persistent-memory mutation is scheduled.
- Replaced the citizen generator's unsynchronized save handoff with
  `MemorySaveCoordinator`. Generation and save authorization may complete in either
  order or simultaneously; persistence is dispatched exactly once to the server
  thread. Repeated authorization/generation, cancellation, generation failure, and
  persistence exceptions have deterministic terminal results, and success is only
  reported after persistence returns successfully.
- The new required real-client smoke test exposed an addon-source-set runtime defect
  that compile/build tests missed: development runs registered only `main`, so normal
  mod code could throw `NoClassDefFoundError` for public API classes. Both loader run
  configurations now register `main` and `addonApi` as the same Talking Colonists mod,
  while the distributable mod jar still embeds the API and the developer `-api` jar
  remains stripped to supported API classes only.
- Replaced the old mixin-only smoke concept with one required client launch smoke test.
  It starts a real client for both loaders, automatically handles the accessibility
  onboarding screen, creates a fresh disposable `MC_Talking_Smoke` world, enters it,
  waits 60 client ticks, and then closes. Headless runs use Xvfb when available; known
  mod-loading/crash markers terminate the process group immediately rather than leaving
  an error screen open. Pre-commit/CI use a content fingerprint marker to require fresh
  launch verification for launch-relevant changes.

### Validation

- `./gradlew test --no-daemon --max-workers=1` passed in Gemini Live Library 2.3.5 on
  both supported loader projects.
- `./gradlew buildAndCollect --no-daemon --max-workers=1` passed in Gemini Live Library.
- Talking Colonists `./gradlew test --no-daemon --max-workers=1` and
  `./gradlew buildAndCollect --no-daemon --max-workers=1` passed against the local
  Gemini Live Library 2.3.5 composite build.
- `CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh` passed in
  the Laptop MCP sandbox on both NeoForge 1.21.1 and Forge 1.20.1 after the `addonApi`
  runtime registration fix, each creating a fresh world and reaching
  `MC_TALKING_AUTOQUIT_SUCCESS:world`. The metadata-only flag was needed because the
  sandbox could not reach Mojang's vanilla asset-object CDN; it does not skip mod
  construction, mixin application, world creation, or in-world ticks.
