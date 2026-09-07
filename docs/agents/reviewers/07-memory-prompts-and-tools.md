# Memory, prompts, facts, and AI tools reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Review factual context generation, persistent/conversation memory, prompt composition,
rumors/broadcasts, and AI tool execution. Primary areas include
`conversations/memory/`, prompt views/providers/contributors, `manager/tools/`,
`internal/api/AiTool*`, `broadcast/`, `rumor/`, and relevant support/helpers.

## Invariants to stress

- Base-game facts presented to Gemini are derived from authoritative current state and
  do not turn unknown/optional/unobserved values into confident facts.
- Public prompt views distinguish unavailable, observed, inferred, and confirmed data
  consistently; version compatibility mapping preserves semantics rather than only
  matching names.
- Memory writes preserve provenance, type, citizen/player identity, relationship
  dimension, and confirmed-vs-model-generated status. Compaction/merging must not
  promote generated text into verified fact.
- Concurrent or delayed memory saves cannot overwrite a newer snapshot, attach to the
  wrong conversation/citizen, or apply an outcome twice.
- Structured-output parsing rejects malformed/partial data safely and does not save a
  half-valid response as if it were confirmed.
- Prompt contributions compose deterministically, obey target/kind/session scope, and
  cannot accidentally leak one citizen/session/addon's contribution into another.
- Tool schemas match runtime parameter validation. Tool execution preserves the
  correct actor/citizen/colony context, scope, permission checks, and explicit failure
  outcome before any side effect occurs.
- Query tools remain side-effect free; command tools do not report success before the
  Minecraft-side operation actually succeeded.
- Addon-provided memory/tool/prompt hooks cannot obtain mutable internal state or
  bypass the public registration/lifecycle rules.

For tool findings, trace schema -> registry -> dispatcher -> concrete operation. For
memory findings, trace generation -> parse -> save coordinator -> stored snapshot ->
prompt rendering; bugs often occur between stages rather than within one class.
