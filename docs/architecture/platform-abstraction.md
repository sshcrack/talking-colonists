---
title: Platform Abstraction
ai_instructions:
  goal: |
    Document the multi-loader platform abstraction layer.

  content_sections:
    - "**Why platform abstraction**: The mod supports NeoForge (1.21.1) and Forge (1.20.1)."
      "  Stonecutter manages version-specific code via conditional compilation."
    - "**Platform interface**:"
      "  - Platform.java — common interface all loader-specific code implements."
      "  - NeoforgePlatformImpl.java — NeoForge implementation."
      "  - ForgePlatformImpl.java — Forge implementation."
    - "**Stonecutter conditional compilation**:"
      "  - `/*? if neoforge {*/` / `/*? if forge {*/` comments in shared code."
      "  - Version-specific access transformers in `src/main/resources/aw/`."
    - "**Build system**:"
      "  - build.neoforge.gradle.kts / build.forge.gradle.kts."
      "  - build-logic custom Gradle plugin."
      "  - .sc_active_version manages active version."
    - "**Mixin approach**: Mixins in the mixin package, verified via smoke test."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/platform/Platform.java"
    - "src/main/java/me/sshcrack/mc_talking/platform/NeoforgePlatformImpl.java"
    - "src/main/java/me/sshcrack/mc_talking/platform/ForgePlatformImpl.java"
    - "AGENTS.md — Stonecutter details."
---

# Platform Abstraction

How the mod supports multiple Minecraft versions and mod loaders.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
