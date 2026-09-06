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

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added `CitizenMemoryService`/`CitizenMemorySnapshot` so addons can append simple
  facts/events and inspect memory without casting MineColonies citizen data to the
  Talking Colonists duck interface. Prompt contributions provide a supported place
  for addon-verified current observations such as Errands/Voyager state.
- This does **not** complete task 08. The MineColonies truth-gap audit/base fact
  expansion, explicit observation-vs-unknown freshness semantics, provenance data,
  stable participant attribution, idempotent addon-confirmed events, relationship
  change provenance, save migration, and save/reload fixtures remain.
