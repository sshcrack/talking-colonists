---
title: Mumbling
ai_instructions:
  goal: |
    Document the ambient mumbling system.

  content_sections:
    - "**What it is**: Citizens occasionally mumble to themselves when idle,"
      "  creating ambient life in the colony."
    - "**How it works**:"
      "  - Periodic check based on mumblingCheckIntervalTicks."
      "  - Probability controlled by mumblingChance."
      "  - Citizens mutter about their current tasks, needs, or thoughts."
      "  - Uses the MumblingTriggerDevice item to trigger."
    - "**Related handler**: CitizenMumblingHandler."
    - "**Config keys**: mumblingChance, mumblingCheckIntervalTicks."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/handler/CitizenMumblingHandler.java"
    - "src/main/java/me/sshcrack/mc_talking/item/MumblingTriggerDevice.java"
    - "src/main/java/me/sshcrack/mc_talking/util/MumblingTopicHelper.java"
---

# Mumbling

Ambient citizen mumbling.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
