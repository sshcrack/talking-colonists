---
title: API Reference
ai_instructions:
  goal: |
    Document the mod's API for other developers who want to extend or integrate with it.

  content_sections:
    - "**PromptProvider SPI** (`api/prompt` package):"
      "  - `CitizenPromptProvider` — Interface to implement for custom prompt generation."
      "  - `CitizenPromptService` — Registry where providers are registered."
      "  - `CitizenPromptView` — Data object passed to providers with all context."
      "  - Example: implementing a custom provider that adds mod-specific context."
    - "**Tool registration** (`manager/tools/AITools.java`):"
      "  - How AI function-calling tools are registered."
      "  - Extending with custom tools by adding to the tool list."
      "  - Tool interface: FunctionAction with execute method."
    - "**Platform abstraction** (`platform/` package):"
      "  - `Platform` interface — Implement for new loader versions."
      "  - `NeoforgePlatformImpl` and `ForgePlatformImpl` as reference."
    - "**MineColonies integration** (`duck/` package):"
      "  - Duck-type interfaces for MineColonies extension points."
      "  - `CitizenDataPersonalityExtended` mixin adds personality storage."
    - "**Events**: Listen to ColonyEventSubscriber for colony events."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/api/prompt/"
    - "src/main/java/me/sshcrack/mc_talking/manager/tools/AITools.java"
    - "src/main/java/me/sshcrack/mc_talking/platform/"
    - "src/main/java/me/sshcrack/mc_talking/duck/"
    - "src/main/java/me/sshcrack/mc_talking/listener/"
---

# API Reference

For developers integrating with or extending the mod.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
