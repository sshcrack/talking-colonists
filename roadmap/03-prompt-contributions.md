# 03 — Composable prompt contributions

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Extend `api/prompt` so multiple addons can contribute context while the existing
`CitizenPromptProvider` and `setProvider` contract continue to work. Inspect all
prompt assembly paths, including player, citizen, pregenerated, and live updates.
Provide namespaced registration, deterministic ordering, duplicate handling, and
documented registration lifetime. A broken contributor should produce a diagnostic
and leave other contributors usable.

Define a small contribution value carrying source, section, and observation versus
recollection semantics. Bound contribution size and total prompt budget. Gather
world-dependent data on the server thread and pass immutable snapshots to generation.
Support session-specific context such as an agenda without replacing global context.
Keep factual content separate from instructions and preserve core safety/behavior rules.

## Acceptance

- Two example contributors and a custom legacy provider coexist in stable order.
- Tests cover duplicate IDs, contributor exceptions, budget limits, and immutable
  snapshots; registration/reset behavior does not leak between server lifetimes.
- All intended conversation modes receive contributions exactly once.
- Document a minimal addon registration example, thread rules, and compatibility.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added namespaced, ordered `CitizenPromptContributor` registration on top of the
  existing replaceable `CitizenPromptProvider`. Contributions are typed as current
  observations, recollections, or addon guidance, are size-bounded, and a failing
  contributor is logged/skipped without breaking other contributors.
- Wired contributors into citizen roleplay, system-controlled roleplay,
  conversational info, basic info, and detailed info surfaces; pregeneration still
  uses the system-controlled prompt plus a separate pregeneration modifier hook.
- Added documentation and compile-checked addon examples. Registration handles are
  process-lifetime unless explicitly closed; server cleanup intentionally does not
  erase mod registrations.
- This does **not** complete task 03. Acceptance tests for two contributors plus a
  legacy provider, duplicate/error/budget behavior, server-lifetime semantics, and
  exactly-once coverage across all modes remain. Session-specific contribution
  context is currently supplied by controlled-session directives rather than a
  first-class contributor context object.
