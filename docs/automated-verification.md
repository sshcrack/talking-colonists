# Automated verification

Local verification on 2026-09-08: 134 mod tests passed per loader; 17 offline library
tests passed per loader (the live test is intentionally skipped in offline runs).
The separately opted-in real Gemini test passed with one session and no retries.
The complete `verify-local.sh` sequence also passed both real client/world checks;
the generated smoke marker covers the final staged runtime/API/build content.

## Laptop: one command

With the sibling `../gemini-live-library` checkout present (or `GEMINI_LIVE_LIBRARY_DIR`
set), stage intended runtime/API/build changes and run:

```sh
bash scripts/verify-local.sh
git add .client-smoke-verified
```

This builds release artifacts, runs both mod suites, runs both library suites against
real loopback WebSockets, and launches both Minecraft clients serially. No Gemini key
is needed for this path. The disposable in-world fixture uses a local provider and
temporarily disables external Gemini access without persisting config changes.

A title screen is insufficient. Success requires entering a world, creating a real
MineColonies citizen, constructing its prompt/tools, playing queued text/audio output,
recovering from an injected provider disconnect, verifying post-reconnect playback,
and releasing foreground ownership. It also verifies the public addon status query.
The local-provider fixture additionally promotes an ambient session into a player-owned
session, encodes a synthetic 48 kHz frame through Simple Voice Chat Opus, routes it
through the participation-gated live client, waits for session-owned generated padding,
and returns the response only after that padding arrives. This exercises the real
orchestration path without a microphone device or Gemini credential and catches padding
that continues after response startup. Both `MC_TALKING_RUNTIME_SUCCESS` and
`MC_TALKING_AUTOQUIT_SUCCESS:world` are required.
Failure logs are retained under `/tmp/client-smoke-*.log`.

## Headless server GameTests

Server-side behaviour backed by real MineColonies state runs as loader GameTests on a
headless GameTest server (no client, no Xvfb, no Gemini key):

```sh
./gradlew :1.21.1-neoforge:runGameTestServer
./gradlew :1.20.1-forge:runGameTestServer
```

Each task starts a fresh flat test world in `versions/<version>/run/gametest/`, runs every
test registered in the `mc_talking` namespace, and exits with the number of failed
required tests, so any failure fails the Gradle task. Look for
`All N required tests passed :)` or `N required tests failed :(` followed by the failing
test names in the output (or `versions/<version>/run/gametest/logs/latest.log`).

Tests live in the dev-only `gameTest` source set (`src/gameTest/{java,resources}`),
created by `configureGameTests()` in `build-logic/`. The release `jar`/`reobfJar` only
package `main` and `addonApi`; `verifyReleaseJarExcludesGameTests` (run after every
`jar` and by `check`) fails if a `me/sshcrack/mc_talking/gametest/` class or the test
template ever lands in the release jar. Stonecutter preprocesses the
source set like `main`, so `/*? if neoforge {*/` conditionals work there.

- `ColonyTestHarness` creates a real colony owned by a loader fake player inside the
  test structure, spawns AI-disabled citizens, places hut blocks through MineColonies'
  own `setPlacedBy` registration, assigns homes/jobs through the building modules, and
  puts citizens to sleep in a real bed through the citizen sleep handler. It clears the
  Gemini key for its lifetime (never saved) and deletes the colony on `close()`.
- Tests use the `mc_talking:empty_floor` template (9x4x9 stone floor) and one batch per
  test, so only one fixture colony exists at a time.
- Current tests: `promptViewReflectsHousingAndJob` (public `CitizenContextService`
  snapshot before/after a real residence + builder hut assignment) and
  `eligibilityRejectsSleepingCitizen` (awake control is eligible; asleep is `SLEEPING`
  for every `ConversationKind`). The ambient speech budget test is pending Q4.

To add a test, add a `public static void name(GameTestHelper helper)` method annotated
with `@GameTest(template = "empty_floor", batch = "<unique batch>")` to a class annotated
with `@GameTestHolder("mc_talking")` and `@PrefixGameTestTemplate(false)`, build state
with `ColonyTestHarness` in try-with-resources, assert with `helper.assertTrue`, and end
with `helper.succeed()`.

## Optional real Gemini check

```sh
bash scripts/test-gemini-websocket.sh live
# Or append --live to verify-local.sh for the complete sequence.
```

Uses one short audio-output session, maximum 64 output tokens, no retries, and bounded
setup/turn waits. Do not run concurrently with gameplay or another live check. This
does not enable billing; use a free-tier project/key. The test cannot inspect the
project's billing settings. A quota/network failure is reported, not retried away.

Credentials are read directly from `GEMINI_LIVE_TEST_KEY_FILE`, `GEMINI_API_KEY`, or
`GEMINI_LIVE_TEST_CONFIG`. With none set, the script first tries `/tmp/gemini-live-key.key`,
then the NeoForge YACL config. If both a config and key are explicitly supplied, the
test uses the config key. No Forge config copy is required. Never commit any key file.

## CI

Mod CI runs both loader suites, then launches both clients under Xvfb/software
graphics as **parallel matrix jobs** (`client-smoke (1.21.1-neoforge)` and
`client-smoke (1.20.1-forge)`), uploading per-version test reports and launch logs even
on failure. A separate, fast `smoke-marker-check` job checks the committed
`.client-smoke-verified` marker against the mixin-relevant file set (mixin classes,
mixin configs, access transformers/wideners, build logic/loader scripts, Stonecutter
files, and dependency versions — see `AGENTS.md` § "Client Launch Smoke Test") rather
than every runtime/API/resource file. The real client-smoke matrix is the authoritative
gate for mixin correctness and runs regardless of marker state; the marker check exists
to catch a genuinely stale local marker, not to block unrelated changes such as a
prompt or lang-file edit.
A separate `Server GameTests` job (`server-gametests`) runs `runGameTestServer` for both
versions headlessly and uploads the GameTest server logs.
The library CI runs both offline suites. Its separate manual `Live Gemini verification`
workflow accepts `GEMINI_API_KEY` as a repository secret, serializes live workflow runs,
and runs only one session. Ordinary PR/build jobs do not need credentials.

The mod now requires Gemini Live Library **2.4.1**. Local composite development uses
the fixed sibling source; clean CI resolves the published artifact. Publish library
2.4.1 before releasing the mod or expecting that clean CI to resolve its dependencies.
These workflow changes have been validated locally, not run on GitHub in this task.

## What this does not prove

This is materially stronger than launch-only smoke testing, but it does not prove
physical microphone capture/device routing, acoustic speech-threshold quality, every
gameplay/tool action, every addon combination, model answer quality, quota availability,
or long-running multiplayer behavior. The in-world microphone reproduction uses real
Simple Voice Chat Opus encode/decode and the mod's orchestration path, but its waveform
and provider are deterministic fakes. The separate live Gemini check covers provider
protocol behavior without a physical microphone. Keep a short manual audio/device and
real conversational barge-in check for releases until hardware-loopback coverage is
available. No Windows handoff is necessary for the automated checks above.
