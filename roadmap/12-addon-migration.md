# 12 — Addon migration and release verification

Dependencies: 01–11.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Review a pinned Colonist Errands source revision and inventory every hook into Talking
Colonists: mixins, reflection, registry access, client access, and prompt replacement.
For each, map to a core fix, a supported extension, or a remaining gap with rationale.
Inspect source and license attribution before incorporating code; prefer focused
behavioral fixes and preserve required notices for copied material.

Provide migration examples for tools, factual contributions, confirmed memory events,
and sessions, plus the Meetings integration guide from task 07. Compile examples
against public interfaces. Document compatibility/deprecation and required library
versions; avoid declaring compatibility with an untested addon release. Prepare
maintainer-facing release notes and a draft response explaining the new integration
surface, without sending messages or publishing releases.

Verify release artifacts without relying on an unpublished composite dependency.
If the required library artifact is not available, document that exact release
blocker and retain the distinction between composite success and release readiness.

## Acceptance

- Every observed Errands internal hook has an explicit migration disposition.
- Public examples compile for both loaders; tests cover simultaneous contributors
  and tools from two addons, session cleanup, permissions, and old-save loading.
- Record in-world validation for player dialogue, paired dialogue, controlled group
  turns, interruption, disconnect, and shutdown; run mixin smoke checks if applicable.
- Both distributable mod artifacts and their library requirements are verified.
- Release notes state implemented behavior, compatibility limits, and remaining
  manual checks. Roadmap statuses match evidence rather than planned outcomes.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Cloned and audited Colonist Errands at commit
  `f270362aca847726087213c623508ad0a65354c1` and Voyager at
  `6666432ec94e58089635dfe8ea3cc6967b59f12f`. No public Colony Meetings repository
  was found; its integration remains based on the supplied design feedback.
- `docs/colonist-errands-migration.md` inventories every reviewed Errands mixin plus
  direct/reflection access into Talking Colonists and maps each to a public API, a
  core fix, or a remaining gap. `ItemAssistantHammerMixin` and `BlockHutTavernMixin`
  target MineColonies itself and therefore cannot be replaced by a Talking Colonists
  API. `docs/addon-api.md` and compile-checked examples cover tools, prompt facts,
  memory events, activity reservations, ordinary conversations, and controlled
  meeting turns. Public API sources now compile in an isolated `addonApi` source set
  and publish as `me.sshcrack:mc_talking-api`, while the normal runtime mod packages
  the same API classes plus implementation.
- This does **not** complete task 12 because tasks 02–11 are not all complete. The
  simultaneous-two-addon test matrix, provenance/old-save coverage, comprehensive
  in-world validation, final distributable artifact verification, compatibility
  statement against a migrated Errands build, and release notes/draft maintainer
  response still remain.
