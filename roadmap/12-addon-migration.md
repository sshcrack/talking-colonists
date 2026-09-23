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


## Final roadmap review — 2026-09-07

- Re-reviewed tasks 01–11 against their acceptance records and the integrated implementation. The
  full automated contract remains covered by the two-loader unit suites, isolated addon API/example
  compilation, collected artifacts, and required real-client smoke. The review found one release
  compatibility defect: mod metadata accepted every Gemini version above the minimum, including a
  future breaking 3.x. Both loaders now derive and enforce the compatible-major range from the single
  dependency property.
- Talking Colonists is intentionally source-breaking for addon developers: API generation 2 removes
  legacy addon methods/adapters and is now released as `2.0.0` rather than another `1.7.x` build.
  Existing normal-user save data remains migration-covered; the major bump reflects the addon API
  contract break.
- Gemini Live Library changes since published `2.3.4` retain existing public constructors/methods and
  add structured Flash output, Live translation support, and structured TTS HTTP failure details.
  They are additive rather than breaking, so the release candidate is `2.4.0` (minor), not `3.0.0`.
  Talking Colonists now requires `2.4.0` and constrains runtime metadata to `[2.4.0,3.0.0)` so a future
  breaking Gemini major is not silently accepted.
- Task 12 remains `Partial` for the same release-only/manual reasons: Gemini Live Library `2.4.0` must
  be published for both loader coordinates, then the credentialed in-world release-candidate matrix
  must be recorded.
- Final validation against the versioned candidates passed:
  - Gemini Live Library `2.4.0`: `./gradlew test buildAndCollect --no-daemon --max-workers=1`, plus
    local-only `publishToMavenLocal` for downstream validation.
  - Binary API comparison against published Gemini `2.3.4` found all 82 pre-existing top-level
    library classes present and zero removed/changed public members.
  - Talking Colonists `2.0.0`: `./gradlew test buildAndCollect verifyApiJar compileAddonApiExamples
    --no-daemon --max-workers=1` against the local `2.4.0` candidate on both loaders.
  - Real-client smoke passed NeoForge 1.21.1 and Forge 1.20.1 after world entry with fingerprint
    `c9671e005fe6ee64148d9b61009a9fad6d50ea7272d1230939ccd175f4e3a2bb`.
  - `scripts/verify-release-readiness.sh` correctly exits `2` because both public Gemini `2.4.0`
    POM/JAR coordinates still return HTTP 404.

## Implementation record — 2026-09-24 (published-library release verification, still partial)

- Gemini Live Library `2.4.0` was never published (reconfirmed HTTP `404` for POM and JAR on both
  `2.4.0-1.21.1-neoforge` and `2.4.0-1.20.1-forge`). `2.4.1` **is** published for both loader
  coordinates: `curl` against `https://maven.sshcrack.me/releases/me/sshcrack/gemini_live_lib/`
  returned HTTP `200` for the POM and JAR of `2.4.1-1.21.1-neoforge` and `2.4.1-1.20.1-forge`.
  `gradle.properties` already declared `deps.gemini_live_lib_version=2.4.1`, so no dependency-version
  edit was needed this pass; `docs/addon-release-readiness.md` previously still said `2.4.0` in prose
  and has been corrected to `2.4.1` with a historical note tracing the earlier `2.3.6`/`2.4.0` targets.
- Verified the release build resolves against the published artifact, not a composite build, by
  running (under this repo's shared Gradle lock, `GRADLE_USER_HOME=/home/hendrik/.gradle`):
  `GEMINI_LIVE_LIBRARY_DIR=/nonexistent ./gradlew buildAndCollect --no-daemon` (invoked via
  `scripts/verify-release-readiness.sh`, see below). Gradle's `settings.gradle.kts` printed
  `Warning: Gemini Live Library not found, skipping includeBuild`, confirming the composite path was
  not used.
- `scripts/verify-release-readiness.sh` (`GRADLE_USER_HOME=/home/hendrik/.gradle bash
  scripts/verify-release-readiness.sh`, `GEMINI_LIVE_LIBRARY_DIR` forced nonexistent internally)
  initially failed with `Missing collected 1.21.1-neoforge mod/API artifact under build/libs` despite
  a successful `BUILD SUCCESSFUL`. Root cause: the script derived `MOD_VERSION` from only
  `mod.version` ("2.0.0") in `stonecutter.properties.toml`, never appending `mod.channel_tag`
  ("-beta.2"), so it searched for `mc_talking-2.0.0-*.jar` while the actually-collected artifacts are
  named `mc_talking-2.0.0-beta.2-*.jar` under `build/libs/2.0.0-beta.2/` (matching how
  `build-logic/src/main/kotlin/Context.kt`'s `fullVersion` is derived). Fixed the script to read both
  properties and concatenate them. After the fix, a full rerun passed end to end (exit `0`):
  - Both normal mod JARs and both stripped addon-API JARs were produced and verified:
    `mc_talking-2.0.0-beta.2-{forge+1.20.1,neoforge+1.21.1}.jar` and the matching
    `mc_talking-api-2.0.0-beta.2-*` files.
  - Each mod JAR's `mods.toml`/`neoforge.mods.toml` was extracted and confirmed to constrain
    `gemini_live_lib` to `versionRange = "[2.4.1,3.0.0)"`, matching the dynamically-derived
    `compatibleMajorVersionRange` from `deps.gemini_live_lib_version=2.4.1` (no manual metadata edit
    was needed; the range is computed from the configured version, not hard-coded).
  - `verifyApiJar` confirmed no implementation classes or installable mod metadata leaked into the
    developer API JARs, for both loaders.
- Did not run `bash scripts/test-client-smoke.sh` in this pass: no launch-relevant runtime, API,
  resource, or build/loader configuration changed (only `scripts/verify-release-readiness.sh`'s
  bash-level version parsing and documentation prose changed), so `AGENTS.md`'s smoke-test trigger
  does not apply. If the pre-commit hook disagrees, that will be resolved by actually running the
  smoke test rather than fabricating a marker.
- Task 12 remains **Partial**. Everything achievable from this checkout without a real Gemini API key
  is now done: the published-library dependency question is reconciled (2.4.1, not 2.4.0), the release
  build/verification path works end to end against the public repository with the composite build
  provably excluded, both loaders' normal and API artifacts are produced and checked, and the
  release-readiness script's own version-parsing bug is fixed. The sole remaining acceptance gap is
  the credentialed in-world validation matrix in `docs/addon-release-readiness.md`
  ("In-world validation record"), which requires a real Gemini API key and a human playing in a world
  and cannot be performed by this agent. Remaining maintainer checklist:
  - [ ] Player dialogue: start/stop a citizen conversation; confirm the audible tail drains and
        ownership releases.
  - [ ] Paired dialogue: two citizens converse; audio follows participants; final playback drains.
  - [ ] Controlled group turns: 3+ citizens take caller-selected turns with attributed shared history.
  - [ ] Interruption: barge-in/caller interruption stops the exact active turn; late audio is ignored.
  - [ ] Provider disconnect: bounded recovery or a typed terminal failure occurs, with no leaked
        busy/provider slot.
  - [ ] Server shutdown: active/idle controlled sessions and provider/audio resources close cleanly.
  - [ ] Record date, Minecraft/loader, Talking Colonists build (`2.0.0-beta.2`), and Gemini Live
        Library build (`2.4.1`) alongside each outcome in `docs/addon-release-readiness.md`.
  - [ ] Only after all six pass, mark task 12 Complete in `roadmap/README.md`.
