---
title: AI Tools
ai_instructions:
  goal: |
    Document the function-calling tools available to the Gemini AI.

  content_sections:
    - "**What AI tools are**: Function declarations the AI can invoke during a conversation"
      "  to affect the game world or gather information."
    - "**Full tool list** (AITools.java):"
      "  - DescribeBuildingAction: Describe the citizen's current building."
      "  - DescribeSurroundingsAction: Describe nearby environment."
      "  - DropItemAction: Drop an item from inventory."
      "  - EndConversationAction: End the conversation."
      "  - GetCitizenInfoAction: Get detailed citizen information."
      "  - GetColonyAction: Get colony statistics."
      "  - GetCurrentSituationAction: Get current situation context."
      "  - GetInventoryAction: Get citizen's inventory contents."
      "  - InitiateBroadcastAction: Start a colony-wide broadcast."
      "  - LeaveColonyAction: Citizen threatens to leave."
      "  - ListCitizenAction: List all citizens."
      "  - RecommendJobAction: Recommend a job change."
      "  - RecordRelationshipChange: Record relationship changes."
      "  - AddEventToMemory: Add an event to citizen's memory."
      "  - (plus any additional tools)."
    - "**Tool registration**: All tools registered in AITools class."
    - "**Disabled tools**: Config option disabledTools can remove specific tools."
    - "**Tool execution context**: Tools receive AbstractEntityCitizen, IColony, and"
      "  a JsonObject with parameters."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/manager/tools/AITools.java"
    - "All files in src/main/java/me/sshcrack/mc_talking/manager/tools/"
---

# AI Tools

Function-calling tools available to the Gemini AI.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
