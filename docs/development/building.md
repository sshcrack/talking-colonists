---
title: Building
ai_instructions:
  goal: |
    Document how to build the mod from source, including all Gradle commands
    and Stonecutter multi-loader workflow.

  content_sections:
    - "**Prerequisites**: JDK 21+, Git."
    - "**Clone & setup**:"
      "  ```sh"
      "  git clone https://github.com/sshcrack/talking-colonists"
      "  cd talking-colonists"
      "  ```"
    - "**Build commands**:"
      "  - `./gradlew buildAndCollect` — Build + collect JARs to build/libs/."
      "  - `./gradlew runActiveClient` — Run client for active Stonecutter version."
      "  - `./gradlew runActiveServer` — Run server for active version."
      "  - `./gradlew test` — Run tests."
    - "**Stonecutter**:"
      "  - Active version in `.sc_active_version` (managed by Stonecutter)."
      "  - Switch versions via `./gradlew stonecutter`."
      "  - Conditional compilation with `/*? if neoforge {*/` / `/*? if forge {*/`."
      "  - Version-specific build scripts: build.forge.gradle.kts / build.neoforge.gradle.kts."
    - "**Publishing**:"
      "  - `./gradlew publishMods` — Publish to Modrinth/CurseForge."
      "  - `./gradlew publishModrinth` — Modrinth only."
      "  - Requires .env file with tokens (see .env.template)."
    - "**CI**: uses `./gradlew buildAndCollect --no-daemon`."
    - "**Mixin smoke test**:"
      "  ```sh"
      "  bash scripts/test-mixin-smoke.sh"
      "  ```"
      "  Run after modifying any mixin. Creates .mixin-smoke-verified on success."

  source_references:
    - "AGENTS.md (Build & Run section)."
---

# Building

How to build the mod from source.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
