# Development

Information for developers working on MineColonies Talking Citizens.

---

## Project Overview

| Aspect | Detail |
|--------|--------|
| **Multi-loader** | Stonecutter manages versions for NeoForge (1.21.1) and Forge (1.20.1) |
| **Conditional compilation** | `/*? if neoforge {*/` / `/*? if forge {*/` directives |
| **Platform abstraction** | `Platform` interface with loader-specific implementations |
| **Key packages** | `me.sshcrack.mc_talking` (entrypoints), `.manager` (Gemini clients), `.conversations` (lifecycle), `.config` (YACL), `.api.prompt` (SPI) |

---

## Quick Start

```sh
git clone https://github.com/sshcrack/talking-colonists
cd talking-colonists
./gradlew buildAndCollect
```

!!! tip "Build output"
 Built jars are collected in `build/libs/` ready for use.

---

## Sub-Pages

| Page | What it covers |
|------|----------------|
| [**Building**](building.md) | Build commands, Stonecutter workflow, publishing |
| [**Contributing**](contributing.md) | PR workflow, mixin guidelines, code style |
| [**API Reference**](api.md) | PromptProvider SPI, tool registration, platform abstraction |
