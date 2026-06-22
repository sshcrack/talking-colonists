# Building

## Prerequisites

- JDK 21+
- Git

## Clone and Setup

```sh
git clone https://github.com/sshcrack/talking-colonists
cd talking-colonists
```

## Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew buildAndCollect` | Build the mod and collect JARs to `build/libs/` |
| `./gradlew runActiveClient` | Run the client for the active Stonecutter version |
| `./gradlew runActiveServer` | Run the server for the active version |
| `./gradlew test` | Run automated tests |
| `./gradlew publishMods` | Publish to Modrinth and CurseForge |
| `./gradlew publishModrinth` | Publish to Modrinth only |

## Stonecutter Workflow

The project uses [Stonecutter](https://github.com/kikugie/stonecutter) for multi-loader version management.

- **Active version**: Stored in `.sc_active_version` (managed by Stonecutter).
- **Switch versions**: Run `./gradlew stonecutter` to switch the active version.
- **Conditional compilation**: Use `/*? if neoforge {*/` and `/*? if forge {*/` comments.
- **Build scripts**: `build.forge.gradle.kts` / `build.neoforge.gradle.kts`.
- **Access transformers**: Version-specific files in `src/main/resources/aw/`.

## Publishing

Requires a `.env` file with API tokens (see `.env.template`):

```sh
PUB_MODS_ENABLE=true
PUB_DRY_RUN=false
# Tokens for Modrinth and CurseForge
```

## CI

CI uses `./gradlew buildAndCollect --no-daemon`.

## Mixin Smoke Test

After modifying any mixin class, verify it loads on all supported versions:

```sh
bash scripts/test-mixin-smoke.sh
```

This creates a `.mixin-smoke-verified` file on success. Commit this file alongside your mixin changes.
