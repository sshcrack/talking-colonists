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
  turns, interruption, disconnect, and shutdown; run the required client launch smoke check.
- Both distributable mod artifacts and their library requirements are verified.
- Release notes state implemented behavior, compatibility limits, and remaining
  manual checks. Roadmap statuses match evidence rather than planned outcomes.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Cloned and audited Colonist Errands at commit
  `f270362aca847726087213c623508ad0a65354c1` and Voyager at
  `6666432ec94e58089635dfe8ea3cc6967b59f12f`. No public Colony Meetings repository
  was found; its integration remains based on the supplied design feedback.
- `docs/addon-migration.md` maps the reviewed legacy integration patterns plus
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

## Implementation record — 2026-09-07 (release verification pass, partial)

- Revalidated the pinned Colonist Errands audit at
  `f270362aca847726087213c623508ad0a65354c1` and its GPL-3.0 license. The migration
  guide now inventories every observed Talking Colonists-facing mixin and the
  non-mixin internal/reflection categories found in that source, with an explicit
  public-API, core-fix, or intentionally-internal disposition. No Colonist Errands
  source code was copied into Talking Colonists. The two MineColonies-targeting
  mixins remain correctly outside this API migration.
- Added `AddonCoexistenceContractTest`, which exercises prompt contributors and tools
  from two independent addon namespaces at the same time, deterministic ordering,
  and ownership-safe unregister/cleanup. Existing tests cover current permission
  rechecks/authoritative actors, asynchronous tool cleanup, controlled-session
  interruption/end behavior, ownership registries at shutdown, and legacy memory
  save migration/idempotency. Public compile examples and stripped-API verification
  pass for both supported loaders.
- Added `docs/addon-release-readiness.md` with the API/loader/library compatibility
  baseline, explicit non-claims for untested external addon releases, the remaining
  credentialed in-world matrix, maintainer-facing release notes, and a draft addon
  maintainer response. `scripts/verify-release-readiness.sh` reads the configured
  Gemini library version dynamically and refuses to treat a local composite or cache
  as release evidence before checking the exact public Maven coordinates.
- Validation passed:
  - `GRADLE_USER_HOME=/cache/gradle ./gradlew :1.21.1-neoforge:test
    :1.21.1-neoforge:verifyApiJar :1.20.1-forge:test
    :1.20.1-forge:verifyApiJar --no-daemon --max-workers=1`
  - `GRADLE_USER_HOME=/cache/gradle ./gradlew buildAndCollect --no-daemon
    --max-workers=1`, producing normal mod and stripped addon-API artifacts for both
    Forge 1.20.1 and NeoForge 1.21.1.
  - `GRADLE_USER_HOME=/cache/gradle CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash
    scripts/test-client-smoke.sh`; both real clients entered a world and emitted the
    required success marker. The metadata-only fallback was used because the sandbox
    stalled on Mojang's asset-index/CDN path before client launch.
- Release readiness is **blocked** rather than falsely marked successful: on
  2026-09-07 the public Maven repository returned HTTP 404 for both
  `me.sshcrack:gemini_live_lib:2.3.6-1.21.1-neoforge` and
  `me.sshcrack:gemini_live_lib:2.3.6-1.20.1-forge`. Repository metadata also did not
  list either 2.3.6 variant. `scripts/verify-release-readiness.sh` therefore exits 2
  before a release build, as intended.
- Task 12 remains **Partial**. All implementation/test/documentation work that can be
  completed in this checkout is in place, but acceptance still requires publishing
  the configured Gemini 2.3.6 artifacts and then recording credentialed in-world
  release-candidate validation for player dialogue, paired dialogue, controlled
  group turns, interruption, provider disconnect, and shutdown. No existing Colonist
  Errands release is declared API-generation-2 compatible until a migrated addon
  build is compiled and tested against the declared targets.
