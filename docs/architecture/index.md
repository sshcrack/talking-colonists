---
title: Architecture Overview
ai_instructions:
  goal: |
    Provide a high-level overview of the mod's architecture. Describe the major
    subsystems and how they interact.

  content_sections:
    - "**Mod structure at a glance**:"
      "  - Entrypoints: McTalking (common), McTalkingClient (client), McTalkingVoicechatPlugin."
      "  - ConversationManager: Central orchestrator for all conversations."
      "  - Config: YACL-based, categories for API/General/Citizens."
      "  - Gemini client layer: GeminiWsClient, CitizenWsClient."
      "  - Prompt system: Prompt providers, views, and the SPI for customization."
      "  - AI Tools: 18+ function-calling tools the AI can invoke."
      "  - Memory: Conversation memory, compaction, relationship tracking."
      "  - Services: Rumor mill, broadcast, pregeneration, urgent contact."
    - "**Multi-loader architecture**:"
      "  - Stonecutter manages two version sets (1.20.1-forge, 1.21.1-neoforge)."
      "  - Platform abstraction via Platform interface and Impl classes."
    - "**Data flow diagram** (describe what a diagram would show):"
      "  Player voice → Voice Chat Plugin → ConversationManager → GeminiWsClient"
      "  → Gemini API → Response → Audio playback."
    - "**Links to sub-pages**."

  source_references:
    - "All files under src/main/java/me/sshcrack/mc_talking/"
    - "AGENTS.md"
---

# Architecture

High-level architecture of MineColonies Talking Citizens.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
