# Track T — Testing and verification infrastructure

Make verification cheaper and more targeted. The client smoke test exists to prove that **mixins
inject correctly** on both loaders; it should gate mixin-relevant changes, not every runtime edit.
Server-side behaviour moves to fast headless tests, and CI becomes the authoritative gate.

| Task | Summary | Depends on | Size | Wave |
| --- | --- | --- | --- | --- |
| T1 | Scope the smoke gate to mixins; CI smoke is the real gate; faster CI | — | M | 0 |
| T2 | Public API binary-compatibility check | — | S | 0 |
| T3 | Prompt view fixture and prompt snapshot tests | — | M | 1 |
| T4 | Headless server GameTests on both loaders | — | L | 1 |
| T5 | Live prompt behaviour checks using the game config key | T3 | M | 1 |

---

## T1 — Scope the smoke gate to mixins; CI smoke is the real gate; faster CI

### Agent prompt

The local `.client-smoke-verified` marker is currently required for any change under
`src/main/java`, `src/main/resources`, `src/api`, build files, and scripts
(`scripts/check-client-smoke-required.sh`). CI already runs the same launch under Xvfb
(`.github/workflows/build_reusable.yml`). The marker causes guaranteed merge conflicts between
branches, goes stale after every merge, and serializes parallel work.

1. Restrict the local gate to changes that can affect mixin application: the mixin package
   (`src/main/java/me/sshcrack/mc_talking/mixin/**`), mixin configs (`*.mixins.json`), access
   transformers/wideners (`src/main/resources/aw/**`), loader metadata templates, build logic and
   loader build scripts, Stonecutter files, and dependency versions (`gradle.properties`,
   `stonecutter.properties.toml` — a MineColonies bump can break mixin targets). Keep the
   fingerprint mechanism, but compute it only over that set so unrelated edits do not invalidate it.
2. In CI, always run the real client launch for both loaders as **parallel matrix jobs** (one per
   Stonecutter version) instead of serially, and make the marker-staleness job check only the
   mixin-relevant set. Keep log upload on failure.
3. Document a fast local loop in `AGENTS.md`: `./gradlew :1.21.1-neoforge:test` while iterating,
   both loaders before pushing, `bash scripts/test-client-smoke.sh` when mixin-relevant files change.
4. Update `AGENTS.md` ("Required Client Launch Smoke Test") and `docs/automated-verification.md`
   to state the purpose (mixin injection) and the new scope.
5. Report the exact CI job/check names so the maintainer can mark the smoke jobs as required
   status checks on `main`.

### Acceptance

- Editing only e.g. a prompt string no longer requires the marker; editing a mixin, a mixin
  config, an access transformer, or a dependency version still does.
- CI runs both loaders' launches in parallel and fails if either fails.


### Implementation record — 2026-09-24

- Gate and fingerprint cover only mixin-relevant files; `client-smoke-fingerprint.py relevant()` is
  the single source of truth and `check-client-smoke-required.sh` calls its `changed` mode.
- CI: `smoke-marker-check` (fast, runs `scripts/tests/test_smoke_gate.py`, then compares the marker
  over the mixin-relevant set) and a parallel `client-smoke` matrix, one job per Stonecutter version.
  `test-client-smoke.sh <version>` runs one loader and never writes the marker.
- `AGENTS.md` and `docs/automated-verification.md` state the purpose (mixin injection), the gated set,
  and the fast local loop.
- Required-check names to add on `main`: `build / Client Smoke (1.21.1-neoforge)`,
  `build / Client Smoke (1.20.1-forge)`, `build / smoke-marker-check` (confirm in the Actions UI).
---

## T2 — Public API binary-compatibility check

### Agent prompt

Enforce the policy in `AGENTS.md` (no breaking changes to `src/api` within generation 2) in the
build. Add a Gradle task (japicmp, or an equivalent maintained plugin) that compares the current
`mc_talking-api` developer jar against a baseline — the last published artifact from
`https://maven.sshcrack.me/releases`, or a checked-in API signature dump if no artifact is
published for a loader. Fail on removed/changed public members, allow additions. Run it in CI on
every PR and in `./gradlew check`. Document how to update the baseline after an intentional,
maintainer-approved break.

### Acceptance

- A deliberately removed public method fails the task; a new public method passes.
- CI runs it; docs explain the baseline.


### Implementation record — 2026-09-24

- `checkApiCompatibility` (per Stonecutter version, wired into `check`) runs the japicmp 0.26.2 CLI
  through `JavaExec` (no Gradle plugin, so no Gradle-version coupling) against
  `me.sshcrack:mc_talking-api:<api_baseline_version>-<mc>-<loader>` from maven.sshcrack.me.
  Flags: public+protected, binary and source incompatibilities are errors, missing third-party
  classes ignored. Baseline: `api_baseline_version=2.0.0-beta.1` in `gradle.properties`.
- Current API vs 2.0.0-beta.1 passes on both loaders; all Wave 0 additions report as compatible.
- Break experiment: making `CitizenConversationService.isBusy` non-public fails both loaders with
  `METHOD_REMOVED`; reverted.
- CI runs it in the build job; `AGENTS.md` documents the baseline rule.
---

## T3 — Prompt view fixture and prompt snapshot tests

### Agent prompt

Prompt regressions (for example colonists falling back to English with each other) are not caught
because `CitizenPromptView` is hard to construct in tests (`PromptRuntimeTest` uses a reflection
`Proxy`). Add a test fixture builder (`CitizenPromptViewFixture`) with realistic defaults and
fluent overrides for identity, work, housing, wellbeing, colony, and conversation (language).
Add snapshot tests for each prompt path — player roleplay, system-controlled roleplay, Flash
conversation script, TTS request, pregeneration — stored as readable text files under
`src/test/resources/prompt-snapshots/`, with a documented way to regenerate them. Add targeted
assertions for invariants: configured language present in every speech prompt, "building style is
not a complaint" present in the housing section, no markdown instruction missing.

### Acceptance

- Snapshot diffs are readable in review; regeneration is one documented command.
- The language invariant test fails if the language line is removed from any path.


### Implementation record — 2026-09-24

- `CitizenPromptViewFixture` (test sources, `me.sshcrack.mc_talking.testing`): realistic housed
  farmer talking to the colony owner, with memories and happiness modifiers so every section
  renders; fluent overrides per view group plus shortcuts (`language`, `home`, `job`,
  `happiness`, `withoutPlayer`, ...). `secondCitizen()` for multi-participant prompts.
- `PromptSnapshotTest` covers player roleplay, system-controlled roleplay, the Flash script
  request, the TTS request and the pregenerated greeting (system instruction + realtime input).
  `PromptSnapshots` compares against `src/test/resources/prompt-snapshots/*.txt`;
  `UPDATE_PROMPT_SNAPSHOTS=1 ... --rerun` rewrites them. The same snapshots pass on both loaders.
- `PromptInvariantTest`: language in every speech path, the building-style note, markdown ban.
- Internal changes needed for determinism/testability (no public API change):
  `MiscUtil.withFirstPicks`; `DefaultCitizenPromptProvider(Supplier<PromptLimits>)` because
  `McTalkingConfig` cannot load outside the game; package-visible `getFlashPrompt`,
  `getTTSPrompt`, `participantInfo`; `PregenerationPrompts` for the greeting and cache note.
- Prompt fixes found by the snapshots: happiness used the default locale (`6,5/10` on a German
  JVM), now `Locale.ROOT`; the player-roleplay "plain text." guideline ran into the next line;
  blocking interaction messages had no line breaks.
- Break experiment: removing the language sentence from the TTS prompt failed
  `configuredLanguageReachesEverySpeechPrompt` and the `tts-request` snapshot; reverted.

---

## T4 — Headless server GameTests on both loaders

### Agent prompt

Most behaviour is server-side (eligibility, cooldowns, ambient budget, prompt views built from real
MineColonies data), but the only in-world verification is the client smoke launch. Set up the
loader GameTest framework (NeoForge and Forge) so server tests run headless via a Gradle task
(`runGameTestServer` or equivalent) in both Stonecutter versions and in CI. Provide a small harness
for spawning a MineColonies colony and citizens in a test structure, and add initial tests:
a citizen's prompt view reflects real housing/job state; conversation eligibility rejects a sleeping
citizen; the Q4 ambient budget blocks a burst of greetings near a player (dropped: see the implementation record). No Gemini access: use the
existing local/fake provider used by `DevRuntimeVerification`.

### Acceptance

- `./gradlew :1.21.1-neoforge:runGameTestServer` and the Forge equivalent pass headless; CI runs them.
- A deliberately broken eligibility rule makes a GameTest fail.

### Implementation record (2026-09-24, branch `roadmap/t4-gametests`)

- Tasks: `:1.21.1-neoforge:runGameTestServer` and `:1.20.1-forge:runGameTestServer`
  (ModDevGradle `gameTestServer` run type, `mc_talking` namespace only, fresh world in
  `versions/<v>/run/gametest/`). The server exits with the failed-required-test count, so any
  failure fails Gradle.
- Exclusion: tests live in a dev-only `gameTest` source set (`src/gameTest`, created by
  `configureGameTests()` in `build-logic/GameTests.kt`). It is added to the dev mod definition
  but never to `jar`/`reobfJar`; `verifyReleaseJarExcludesGameTests` (after `jar`, and in
  `check`) guards it. A source set was chosen over the `devtools` Stonecutter switch because it
  cannot ship by construction and needs no active-source rewrite. Stonecutter preprocesses it.
- Harness: `ColonyTestHarness` creates a real colony (loader `FakePlayer` owner, `Colonial`
  pack) in the `mc_talking:empty_floor` template, spawns AI-disabled citizens, places huts via
  MineColonies `setPlacedBy`, assigns home/job through building modules, sleeps citizens via the
  sleep handler, clears the Gemini key for its lifetime, and deletes the colony on close.
  One batch per test.
- Tests (pass on both loaders): `promptViewReflectsHousingAndJob` (public
  `CitizenContextService.snapshot` HOMELESS/unemployed before, HOUSED + home/workplace level 1 +
  builder job name after) and `eligibilityRejectsSleepingCitizen` (awake control eligible;
  asleep rejected as `SLEEPING` for every `ConversationKind`).
- Break experiment: disabling the `isSleeping()` check in
  `ConversationManager.conversationEligibility` failed `eligibilityrejectssleepingcitizen` on both
  loaders (`1 required tests failed :(`, non-zero exit); reverted.
- CI: separate `server-gametests` job ("Server GameTests") in `build_reusable.yml`.
- Dropped: a Q4 ambient-budget GameTest. The budget only counts listeners in the server player list and a GameTest cannot add one (fake player not listed; mock player login breaks MineColonies login sync). Covered by `AmbientSpeechBudgetRegistryTest` instead.

---

## T5 — Live prompt behaviour checks using the game config key

**Depends on:** T3

### Agent prompt

Some regressions only show in what the model does. Add an opt-in check (script plus manual
`workflow_dispatch` workflow) that runs a small fixed set of scenarios against Gemini with a cheap
text model, using the T3 prompt fixtures: configured Portuguese produces Portuguese dialogue for a
citizen-to-citizen script; a cavern-style home produces no style complaint; a question answerable by
a tool triggers the tool instead of an invented answer. Evaluate outputs with deterministic checks
where possible (language detection heuristics, keyword absence) and keep the scenario count and
tokens small.

Read the API key the same way the game does: from the YACL config
(`versions/<version>/run/config/yacl-mc_talking.json5`, field `geminiApiKey`; in a git worktree
resolve the main checkout via `git rev-parse --git-common-dir`), with `GEMINI_API_KEY` as an
override and a repository secret in CI. Never print or commit the key. Never run automatically on
push.

### Acceptance

- `bash scripts/test-prompt-behaviour.sh` runs locally with the key from the game config and
  reports per-scenario pass/fail; a missing key is a clear skip, not a failure.
