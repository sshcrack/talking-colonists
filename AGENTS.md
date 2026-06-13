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

## Mixin Smoke Test

After modifying any mixin class (in `src/main/java/me/sshcrack/mc_talking/mixin/`), you **must** verify it loads correctly on all supported versions. Run:

```sh
bash scripts/test-mixin-smoke.sh
```

This script:
1. Discovers all version subprojects (1.21.1-neoforge, 1.20.1-forge) from `settings.gradle.kts`
2. Launches each version's Minecraft client in parallel with `runClientAutoQuit`
3. Each client auto-loads the first singleplayer world and quits after 60 ticks (~3s)
4. Captures Gradle + Minecraft output to `/tmp/mixin-smoke-<version>-*.log`
5. Copies each version's `run/logs/latest.log` alongside the Gradle output for deeper inspection
6. Prints `[PASS]`/`[FAIL]` per version with the log file paths

**What it tests:** The game starts, applies all mixins, enters a world, and shuts down cleanly without a crash. If a mixin has a bad target or causes a class-loading error, the game will fail to start or crash.

**If a version fails:** Read the log file at the printed path and search for `mixin`, `error`, or `Exception`.

### CI Verification

CI does **not** run Minecraft (too slow). Instead, after a successful local run, `scripts/test-mixin-smoke.sh` creates `.mixin-smoke-verified` containing the current commit hash. **Commit this file** alongside your mixin changes:

```sh
bash scripts/test-mixin-smoke.sh   # creates .mixin-smoke-verified on success
git add .mixin-smoke-verified
git commit -m "verify mixin smoke test"
```

The `mixin-smoke-verification` CI job checks that `.mixin-smoke-verified` exists and matches `HEAD`. This is a fast (~10s) required check that blocks PR merge if the smoke test isn't current.

A pre-commit hook (`check-mixin-smoke-required`) **blocks** the commit if mixin files are staged but `.mixin-smoke-verified` doesn't match `HEAD`. Run the smoke test, stage the generated `.mixin-smoke-verified` file, then commit.

If you need to bypass (e.g., CI-only fix): `git commit --no-verify`.

## Local Gemini Live Library

For local development the Gemini Live Library can be included as a composite build at `../gemini-live-library`. When it exists, publishing tasks **fail** unless you confirm with:

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
