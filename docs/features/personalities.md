---
title: Personalities
ai_instructions:
  goal: |
    Document the personality archetype system.

  content_sections:
    - "**What personalities are**: System prompts injected into the citizen's AI prompt"
      "  that shape their behavior, tone, and dialogue style."
    - "**15 archetypes**:"
      "  OPTIMIST, GRUMP, STOIC, GOSSIP, ANXIOUS, BOASTFUL, TIMID,"
      "  PHILOSOPHICAL, SARCASTIC, DRAMATIC, NURTURING, COMPETITIVE,"
      "  CURIOUS, NOSTALGIC, SUPERSTITIOUS."
      "  List each with a 1-sentence description of their personality."
    - "**How assignment works**: Each citizen gets a random personality on creation."
      "  Stored via CitizenDataPersonalityExtended mixin."
    - "**Custom personalities**: Users can define custom archetypes via"
      "  customPersonalityArchetypes config list."
    - "**Config keys**: enablePersonalityArchetypes, customPersonalityArchetypes."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/config/PersonalityArchetype.java"
    - "src/main/java/me/sshcrack/mc_talking/mixin/CitizenDataPersonalityExtended.java"
    - "src/main/java/me/sshcrack/mc_talking/duck/PersonalityExtendedCitizen.java"
---

# Personalities

Personality archetypes that shape citizen behavior.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
