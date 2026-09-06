# AGENTS.md — talking-colonists

Minecraft mod that lets you talk to MineColonies citizens via Google Gemini AI voice chat.

## Multi-loader (Stonecutter)

Uses **Stonecutter** for two versions: `1.21.1-neoforge` (VCS default) and `1.20.1-forge`.

- Active version is in `.sc_active_version` (managed by Stonecutter).
- **Never commit changes to `.sc_active_version`** — the pre-commit hook blocks it. Use `--no-verify` only if intentional.
- Conditional compilation with `/*? if neoforge {*/` / `/*? if forge {*/` comments.
- Version-specific access transformers: `src/main/resources/aw/<version>.cfg` (Forge), `src/main/resources/aw/<version>.accesswidener` (Fabric/NeoForge).
- Build scripts: `build.forge.gradle.kts` / `build.neoforge.gradle.kts`.
- Custom Gradle plugin `mod-platform` defined in `build-logic/`.
- When updating adding / removing configuration values in `McTalkingConfig`, make sure to update the `src/main/resources/assets/mc_talking/lang/en_us.json` translation file.
- ONLY put mixins in the `src/main/java/me/sshcrack/mc_talking/mixin` package. EVERY class in the `mixin` package MUST be a mixin (or accessor).

## Minecolonies Lookup
When working with the Minecolonies API, look at the `scripts/MINECOLONIES_DOCS.md` to view the docs and if you need actual code insight, use the gradle classes / minecolonies sources jar to view the source.

## Build & Run

```sh
./gradlew buildAndCollect                   # build + collect jars to build/libs/
./gradlew runActiveClient                   # run client for active Stonecutter version
./gradlew runActiveServer                   # run server for active version
./gradlew publishMods                       # publish to Modrinth/CurseForge
./gradlew publishModrinth                   # Modrinth only
./gradlew test                              # tests
```

CI uses `./gradlew buildAndCollect --no-daemon`. JDK 25 (Microsoft) in CI.

## Required Client Launch Smoke Test

After changing runtime Java, API Java, resources, loader/build configuration, or the
client-smoke infrastructure itself, you **must** verify that both supported clients
actually launch. Run:

```sh
bash scripts/test-client-smoke.sh
```

The smoke test:
1. Discovers every Stonecutter version (`1.21.1-neoforge`, `1.20.1-forge`).
2. Runs the versions **serially** with `runClientAutoQuit` and one Gradle worker, so
   Minecraft/NeoForm downloads do not compete with each other.
3. Uses an existing singleplayer save when available. On a clean checkout with no
   save, auto mode opens Minecraft's vanilla create-world screen and creates a
   default disposable smoke-test world automatically. A title screen alone never
   counts as success.
4. Requires the explicit `MC_TALKING_AUTOQUIT_SUCCESS:world` marker. Auto mode closes
   itself only after the player has actually entered a world and remained there for
   60 client ticks, with an in-client startup deadline.
5. Watches the Minecraft/Gradle logs for crash markers and terminates the whole
   client process group immediately on a detected crash. A hard external timeout
   also terminates hung clients, so auto mode must not leave a crash/loading window
   open indefinitely.
6. Captures Gradle and Minecraft logs under `/tmp/client-smoke-*.log`. In headless
   environments it automatically uses `xvfb-run` when available.

If the sandbox can reach Mojang metadata but its asset CDN is blocked, use
`CLIENT_SMOKE_METADATA_ONLY_ASSETS=1 bash scripts/test-client-smoke.sh`. This still
launches the real client, creates/enters the smoke world, and exercises mixins/mod
construction; it only skips downloading cosmetic vanilla asset objects.

On success the script writes `.client-smoke-verified`, which contains a fingerprint
of all launch-relevant worktree content. Stage the intended changes before running
the test, then stage the marker:

```sh
git add <intended runtime/build changes>
bash scripts/test-client-smoke.sh
git add .client-smoke-verified
```

The pre-commit hook compares the marker with the staged launch-relevant content and
blocks stale or missing verification. CI independently compares the committed marker
with the committed tree. This avoids the old commit-hash race where a marker could
only describe the parent commit rather than the code being committed.

Mixin changes receive the same real-launch coverage through this required client
smoke test, and `scripts/check-mixin-registration.sh` separately enforces mixin
registration.

If a version fails, inspect the printed Gradle log and its adjacent `-minecraft.log`
copy. Do not fabricate or hand-edit `.client-smoke-verified`.

## Local Gemini Live Library

For local development the Gemini Live Library can be included as a composite build at `../gemini-live-library`, or pointed elsewhere with `GEMINI_LIVE_LIBRARY_DIR`. When a composite library is present, publishing tasks **fail** unless you confirm with:

```sh
./gradlew publishMods -PgeminiPublished=true
# or set GEMINI_PUBLISHED=true
```

## Code Style

- 4-space indent for Java, 2-space for JSON/YAML/Markdown (`.editorconfig`).
- Java: single class imports; import-on-demand threshold = 999.
- No automated formatter or linter configured.

## Key Packages

| Path | Purpose |
|------|---------|
| `me.sshcrack.mc_talking` | Entrypoints: `McTalking` (common), `McTalkingClient` (client), `McTalkingVoicechatPlugin` (voice chat) |
| `.manager` | Gemini client (`GeminiWsClient`, `CitizenWsClient`), prompt providers |
| `.conversations` | Conversation lifecycle, memory management |
| `.mixin` | 9 Mixin classes for entity/event hooks |
| `.platform.*` | Loader-agnostic abstraction (`Platform`, `NeoforgePlatformImpl`, `ForgePlatformImpl`) |
| `.config` | YACL-based config (`McTalkingConfig`), personalities, modes |
| `.api.prompt` | Prompt view/provider SPI |

## Dependencies

Required: MineColonies (LDTTeam), Gemini Live Lib (`me.sshcrack`), Simple Voice Chat, YACL.
Embedded: MixinConstraints via JarJar.

## Publishing

- Tag must match `mod.version` + `mod.channel_tag` from `stonecutter.properties.toml`.
- `.env` file (gitignored, see `.env.template`) controls toggle flags and tokens.
- Dry-run by default; set `PUB_DRY_RUN=false` and `PUB_MODS_ENABLE=true` in `.env`.
- Modrinth project: `EOBBpcat` (repo-default, overridable via `PUB_MODRINTH_PROJECT_ID`).

## Release Workflow

1. Update `mod.version` in `stonecutter.properties.toml`.
2. Push a git tag matching the version.
3. CI validates tag, runs `buildAndCollect`, generates changelog (git-cliff), creates GitHub release, uploads artifacts.
