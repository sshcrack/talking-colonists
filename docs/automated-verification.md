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

Mod CI runs both loader suites and launches both clients under Xvfb/software graphics,
uploading test reports and launch logs even on failure. The existing staged-content
smoke marker policy remains enforced; CI additionally executes the real launches.
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
