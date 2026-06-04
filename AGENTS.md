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
