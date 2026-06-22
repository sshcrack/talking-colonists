---
title: Memory System
ai_instructions:
  goal: |
    Document how citizens remember events, conversations, and relationships.

  content_sections:
    - "**What citizens remember**:"
      "  - Past conversations with players (summaries)."
      "  - Colony events (raids, new buildings, etc.)."
      "  - Relationships with other citizens."
      "  - Player interactions (positive/negative)."
    - "**Memory modes** (memoryMode config):"
      "  - LIVE: Uses text-only WebSocket for memory compaction."
      "  - FLASH: Uses Gemini Flash API for memory compaction."
    - "**Memory compaction**:"
      "  - Periodically, old memories are summarized and compressed."
      "  - Controlled by enableMemoryCompaction, memoryCompactionIntervalTicks,"
      "    memoryCompactionThreshold."
    - "**Data models**:"
      "  - CitizenMemories — stores memories per citizen."
      "  - CitizenRelationshipMemory — inter-citizen relationship data."
      "  - CitizenRelationshipChangeType — how relationships evolve."
    - "**Config keys**: memoryMode, enableMemoryCompaction,"
      "  memoryCompactionIntervalTicks, memoryCompactionThreshold."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/conversations/memory/"
    - "src/main/java/me/sshcrack/mc_talking/conversations/memory/data/"
    - "src/main/java/me/sshcrack/mc_talking/config/MemoryMode.java"
---

# Memory System

How citizens remember events, conversations, and relationships.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
