---
title: Conversations
ai_instructions:
  goal: |
    Document both player-to-citizen and citizen-to-citizen conversation systems.

  content_sections:
    - "**Player ↔ Citizen**:"
      "  - Initiated by left-clicking a citizen with the Citizen Communication Device."
      "  - Uses Gemini Live API for real-time voice chat."
      "  - Citizens respond based on their personality, memories, colony status."
      "  - Conversation lifecycle handle by ConversationManager."
      "  - Slot-based priority system (player conversations always win)."
    - "**Citizen ↔ Citizen**:"
      "  - Auto-triggered by the mod when citizens are near each other."
      "  - Two modes (controlled by conversationMode config):"
      "    - LIVE_WEBSOCKETS: Two Gemini Live sessions cross-fed."
      "    - FLASH_TTS: Flash generates script, TTS renders audio."
      "    - AUTO: Tries FLASH_TTS first, falls back as needed."
      "  - Citizens share rumors, discuss colony events, gossip."
    - "**Random Conversations**: Citizens may randomly start talking."
    - "**Config keys**: conversationMode, enableCitizenToCitizenConversation,"
      "  enableRandomConversations, randomConversationChance, respondInGroups."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/conversations/CitizenConversation.java"
    - "src/main/java/me/sshcrack/mc_talking/conversations/CitizenConversationGenerator.java"
    - "src/main/java/me/sshcrack/mc_talking/conversations/LiveConversationWsClient.java"
    - "src/main/java/me/sshcrack/mc_talking/config/ConversationMode.java"
    - "src/main/java/me/sshcrack/mc_talking/handler/CasualGreetingHandler.java"
    - "src/main/java/me/sshcrack/mc_talking/handler/RandomConversationHandler.java"
---

# Conversations

How citizens talk to players and each other.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
