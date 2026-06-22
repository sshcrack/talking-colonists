---
title: Urgent Contact
ai_instructions:
  goal: |
    Document how citizens initiate contact with players for urgent needs.

  content_sections:
    - "**What it is**: Citizens walk to the player when they have urgent needs"
      "  (low happiness, missing resources, etc.)."
    - "**Behavior flow**:"
      "  - Periodically, citizens assess their needs."
      "  - If needs are urgent enough, the citizen walks to the nearest player."
      "  - UrgentContactHandler manages the walk-to-player behavior."
      "  - Citizens complain about their unmet needs when they arrive."
    - "**Casual greetings**: A lighter version — citizens greet players they walk past."
      "  Handled by CasualGreetingHandler."
    - "**Config keys**: enableCitizenInitiatedContact, citizenContactBaseChance,"
      "  citizenContactCheckIntervalTicks, enableUrgentContactWalkToPlayer,"
      "  urgentContactSearchRange, blockingTaskUrgencyMultiplier,"
      "  playerUrgentContactCooldownSeconds, citizenCasualGreetingWeight."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/handler/UrgentContactHandler.java"
    - "src/main/java/me/sshcrack/mc_talking/handler/CasualGreetingHandler.java"
    - "src/main/java/me/sshcrack/mc_talking/handler/PregeneratedGreetingHandler.java"
---

# Urgent Contact

How citizens seek out players for urgent needs.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
