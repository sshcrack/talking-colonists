# Addon API reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Review the supported addon surface and its implementation boundary. Primary areas are
`src/api/`, `src/main/.../internal/api/`, `src/apiTest/`, addon-facing contract tests,
Gradle source-set/publication wiring, and `docs/addon-*.md`.

## Invariants to test mentally

- An addon compiling against the API artifact should not need or accidentally see
  Talking Colonists internals, mixins, transport classes, audio queues, or concrete
  runtime managers.
- Public signatures must not leak internal types through parameters, return types,
  generic bounds, exceptions, static fields, or implementation-only dependencies.
- Registration, coexistence, duplicate registration, teardown, and lookup behavior
  must be deterministic and safe for multiple independent addons.
- Conversation/session handles must expose enough state to use them correctly without
  letting addons mutate core-owned bookkeeping.
- Tool execution must preserve actor/context, permission/scope checks, schema
  validation, and explicit failure outcomes across the public/internal boundary.
- Prompt, memory, pre-generation, and conversation APIs should document nullability,
  cancellation/failure, lifecycle ownership, and thread expectations where callers
  need them.
- The current policy intentionally allows breaking addon API changes. Do not request
  legacy aliases/shims merely for compatibility; instead flag undocumented or
  internally inconsistent breaks, stale migration docs, or accidental ABI/source
  leakage.
- Compile examples and contract tests should exercise the same artifact boundary an
  external addon sees, not accidentally compile because `src/main` internals are on
  the test classpath.

Trace each changed API method into its backend implementation and at least one caller
or test. Look especially for contracts that appear clean at the interface but become
unsafe because the backend returns mutable/core-owned state or accepts work after the
owning session has ended.
