---
title: Broadcast System
ai_instructions:
  goal: |
    Document the colony-wide broadcast propagation system.

  content_sections:
    - "**What it is**: Players can ask citizens to spread a message across the colony."
      "  Citizens propagate the message to nearby citizens, creating a chain."
    - "**How it works**:"
      "  - Player tells a citizen a message they want broadcast."
      "  - The AI uses the InitiateBroadcastAction tool to start a broadcast."
      "  - Citizens periodically propagate broadcasts to nearby citizens."
      "  - BroadcastPropagationService manages the propagation logic."
      "  - Citizens can yell broadcasts (enableBroadcastYelling)."
    - "**Data model**: ColonyBroadcast stores message, source citizen, timestamp."
    - "**Config keys**: enableBroadcastPropagation, broadcastPropagationIntervalTicks,"
      "  broadcastMaxPropagationsPerTick, broadcastPropagationRange,"
      "  maxBroadcastsInPrompt, maxBroadcastsStored,"
      "  enableBroadcastYelling, broadcastYellingRange."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/broadcast/ColonyBroadcast.java"
    - "src/main/java/me/sshcrack/mc_talking/broadcast/BroadcastPropagationService.java"
    - "src/main/java/me/sshcrack/mc_talking/manager/tools/InitiateBroadcastAction.java"
---

# Broadcast System

Colony-wide message propagation.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
