---
title: Rumor Mill
ai_instructions:
  goal: |
    Document the rumor mill system — how citizens share information with each other.

  content_sections:
    - "**What it is**: Citizens organically share information (rumors) with each other,"
      "  creating a living information economy in the colony."
    - "**How rumors spread**:"
      "  - Periodically, nearby citizen pairs are checked."
      "  - Based on configurable chance per pair, a rumor propagates."
      "  - Citizens can talk about rumors (enableRumorTalking)."
    - "**Rumor data model**: Rumor class stores the rumor text, source, timestamp."
    - "**Prompt integration**: Recent rumors are included in the citizen's AI prompt."
    - "**Config keys**: enableRumorMill, rumorMillChancePerPair, rumorMillRange,"
      "  rumorMillCheckIntervalTicks, rumorMillMaxPropagationsPerTick,"
      "  enableRumorTalking, rumorTalkingChance, rumorTalkingRange,"
      "  maxRumorsStored, maxRumorsInPrompt."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/rumor/Rumor.java"
    - "src/main/java/me/sshcrack/mc_talking/rumor/RumorMillService.java"
---

# Rumor Mill

How information spreads between citizens.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
