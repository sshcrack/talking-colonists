# 08 — Verified facts and memory provenance

Dependencies: 02, 03, 04.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Audit `CitizenPromptView`, its factory, the default provider, information tools,
player memory extraction, and persistent memory data. Compare Errands truth-related
workarounds with current coverage. Record a compact gap table with source locations
and reproduce misleading cases before changing behavior.

Fill verified base MineColonies gaps: real citizen health/equipment, housing status,
builder activity/blockers, and stock/request availability. Preserve distinctions
between zero, unavailable, unloaded, and stale data. Avoid costly full-colony scans
per prompt; document snapshot freshness and refresh relevant facts before actions.
Prompt instructions should prefer current observations over contradictory memories
without claiming that prompting can eliminate all hallucinations.

Add memory provenance sufficient to distinguish observed events, player statements,
citizen statements, and addon-confirmed outcomes. Use stable participant identifiers,
not names, for attribution. A citizen-only transcript cannot establish a player
promise. Accept addon-confirmed events through a supported interface with source
and idempotency ID; leave promise fulfillment/rewards and addon rapport rules outside
core. Preserve existing per-player relationship data and migrate older saves safely.

## Acceptance

- Fixtures cover healthy/equipped citizens, homeless versus unknown housing, a builder
  waiting for materials versus sleeping, and unavailable versus empty inventory data.
- Current facts take precedence over conflicting recollections in assembled context.
- Citizen speech alone cannot create a confirmed player promise; duplicate addon
  events do not duplicate memories or relationship changes.
- Old memory data loads without loss; new provenance survives a save/reload cycle.
- Document extension examples for Errands outcomes and Voyager expedition facts.

## Implementation record — 2026-09-07

Status: **Complete**.

- Audited the core prompt/memory paths and current Colonist Errands truth workarounds in
  `docs/facts-and-memory-audit.md`, including the misleading baseline cases and the
  expensive prompt-time warehouse scan.
- Added `CitizenPromptView.verifiedFacts()` with explicit `CURRENT`, `STALE`, `UNLOADED`,
  and `UNAVAILABLE` observation semantics for health, equipment/inventory, housing,
  request lifecycle, and builder activity. Prompt construction now uses bounded
  citizen/work-building data and no longer scans all colony warehouses.
- Current verified facts are rendered ahead of provenance-labelled recollections with
  an explicit precedence rule. `get_current_situation` rebuilds the snapshot before
  answering instead of treating an older prompt snapshot as live state.
- Added provenance-aware persistent memory entries and relationship contributions with
  stable participant UUIDs. Citizen-only transcript extraction is always
  `CITIZEN_STATEMENT` and is explicitly forbidden from establishing player speech or
  promises. Observed fulfillment is labelled `OBSERVED_EVENT`.
- Added `CitizenMemoryService.confirmOutcome(...)` for source-labelled, durable,
  idempotent addon-confirmed events/facts and relationship effects. The idempotency
  journal survives save/reload and remains effective even if display memory text is
  later removed. Promise policy/rewards remain addon-owned.
- Legacy facts/events/relationship aggregates migrate losslessly as
  `LEGACY_UNATTRIBUTED`; new provenance and relationship contribution metadata survive
  reload. Addon documentation includes Errands promise-fulfillment and Voyager
  expedition examples.
- Regression fixtures cover healthy/equipped, homeless-vs-unknown, sleeping-vs-material
  waiting builders, empty-vs-unavailable inventory, current-fact precedence wording,
  citizen promise claims, duplicate addon outcomes, and old/new save round trips.

Validation:

- `GRADLE_USER_HOME=/cache/gradle ./gradlew test --no-daemon --max-workers 1`
- `GRADLE_USER_HOME=/cache/gradle ./gradlew buildAndCollect --no-daemon --max-workers 1`
- `CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh` (required staged Forge 1.20.1 and NeoForge 1.21.1 real-client smoke; sandbox asset CDN fallback)
