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

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added a shared strict `MemoryResponseParser` and moved both citizen-to-citizen and
  player-memory parsing through it. It accepts plain JSON or one enclosing `json`
  fence, normalizes omitted optional collections, and rejects malformed roots,
  unknown citizens/relationship targets/types, and out-of-range/non-finite deltas
  before persistent memory is mutated.
- Replaced the citizen generator's `shouldSaveMemory` / `savedResponse` race with
  `MemorySaveCoordinator`, which serializes generation, authorization, cancellation,
  failure, and one-shot server-thread persistence. Added deterministic parser and
  coordinator tests.
- This does **not** complete task 02. Opt-in Gemini structured-output MIME/schema
  support and its request-serialization tests are still missing, as is the explicit
  compaction-format verification/library dependency record required by acceptance.
  The standalone task-02 implementation should reconcile with these shared parser
  and coordinator classes instead of creating competing implementations.
