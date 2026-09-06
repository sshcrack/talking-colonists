# 03 — Composable prompt contributions

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Extend `api/prompt` so multiple addons can contribute context while complete prompt
providers can still be registered through the current API. Inspect all
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

- Two example contributors and a custom registered provider coexist in stable order.
- Tests cover duplicate IDs, contributor exceptions, budget limits, and immutable
  snapshots; per-session context does not leak between sessions/server lifetimes.
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


## Completion record — 2026-09-06

- Completed the contributor contract with immutable `PromptContributionContext` and
  `PromptSessionContext`. Contributors now receive the citizen snapshot, exact prompt target, and
  per-session context as one value. Controlled-session agendas are snapshotted per requested turn;
  `setAgenda(...)` affects later turns only and session context is never stored in a global registry.
- Made contribution provenance explicit. `PromptContribution` now requires a single-line `source`
  and `section` plus observation/recollection/guidance semantics. Core renders those categories in
  separate sections, bounds each contribution and the total rendered addon block, isolates failing
  contributors, and appends an explicit core-priority boundary so addon guidance is not permission or
  authority and current observations outrank stale recollections.
- Moved prompt-world reads ahead of provider/background generation. Player/system Live clients,
  pregeneration, paired Live conversations, and Flash/TTS multi-citizen generation now consume
  immutable `CitizenPromptView` snapshots assembled on the Minecraft server thread rather than
  querying MineColonies from provider/WebSocket/background callbacks.
- Added deterministic coverage for a custom registered provider plus two contributors in stable
  order, duplicate IDs/replacement, contributor exceptions, total budget enforcement, exactly-once
  invocation for every `PromptTarget`, immutable per-turn session context, agenda isolation between
  sessions, explicit source rendering, and label validation. The compile-checked public API example
  now includes both verified-state and controlled-meeting-agenda contributors.
- Documented registration lifetime and threading in `docs/addon-api.md`: registrations are
  process/mod-lifetime until their handles are closed and intentionally survive server restarts;
  changing per-session state belongs in `PromptContributionContext.session()`. Contributor callbacks
  must not read/mutate Minecraft or MineColonies world state.
- **Intentional API break:** the user explicitly requested a clean breaking addon-API baseline, so the
  earlier `setProvider` compatibility requirement is superseded. No legacy provider mutation alias or
  deprecated compatibility shim is retained. `AGENTS.md` now records this project policy for future
  agents; migrations belong in `docs/addon-migration.md`.
- Validation against Gemini Live Library 2.3.5 used a locally published copy in a temporary Maven
  cache because 2.3.5 is not yet available from the public repository. This is the existing release
  dependency blocker recorded by task 02, not an item-03 implementation gap.
- Validation: `./gradlew test compileAddonApiExamples --no-daemon --max-workers=1` passed on both
  loaders, and `./gradlew buildAndCollect --no-daemon --max-workers=1` passed with both
  distributable/API artifacts verified (using the temporary local 2.3.5 Maven cache described
  above). `CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh` then passed for
  NeoForge 1.21.1 and Forge 1.20.1, with each real client creating/entering the disposable smoke
  world and producing the in-world success marker before auto-close.
