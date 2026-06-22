---
title: Citizens Configuration
ai_instructions:
  goal: |
    Document the `citizens` config category and all its sub-groups. This is the
    largest section. Include config key, type, default, and description for each option.

  config_fields:
    - "**enableColonyStatsMentions**: Let citizens talk about colony stats."
    - "**citizenInteractionRange** (double): Range for interacting with citizens."
    - "**citizenCooldownSeconds** (int): Cooldown between conversations with same citizen."

    - "**citizen-to-citizen**:"
      "  - enableCitizenToCitizenConversation: Let citizens talk to each other."
      "  - enableConversationSummaryAndMemorize: Save summaries of citizen-citizen chats."
      "  - conversationMode (ConversationMode — LIVE_WEBSOCKETS, FLASH_TTS, AUTO): How citizen-citizen conversations work."

    - "**random_conversations**:"
      "  - enableRandomConversations: Citizens randomly start talking."
      "  - randomConversationChance: Probability per check."
      "  - randomConversationCheckIntervalTicks: How often to check."

    - "**mumbling**:"
      "  - mumblingChance: Probability citizen mumbles."
      "  - mumblingCheckIntervalTicks: Check interval."

    - "**citizen_contact**:"
      "  - enableCitizenInitiatedContact: Citizens walk to player for urgent needs."
      "  - citizenContactBaseChance, citizenContactCheckIntervalTicks."
      "  - enableUrgentContactWalkToPlayer, urgentContactSearchRange."
      "  - blockingTaskUrgencyMultiplier, playerUrgentContactCooldownSeconds."
      "  - citizenCasualGreetingWeight."

    - "**pregeneration**:"
      "  - enablePregeneration: Pre-generate audio for faster responses."
      "  - pregeneratedGreetingDistance, threatPlayCooldownMs."
      "  - maxPregeneratedGreetingsPerCitizen, maxGreetingsPerTickInterval."
      "  - enablePlayerGreetingPregen, playerGreetingDistance."

    - "**voice_chat**:"
      "  - citizenVoiceWhisper: Citizens use whisper volume."
      "  - citizenVoiceDistance: Audio distance for citizen voice."

    - "**raid_trauma**:"
      "  - raidTraumaDurationSeconds: How long citizens remember a raid."

    - "**colony_events**:"
      "  - colonyEventWindowSeconds: Time window for recent colony events."

    - "**rumor_mill**:"
      "  - enableRumorMill, rumorMillCheckIntervalTicks, rumorMillRange."
      "  - rumorMillChancePerPair, rumorMillMaxPropagationsPerTick."
      "  - enableRumorTalking, rumorTalkingChance, rumorTalkingRange."
      "  - maxRumorsStored, maxRumorsInPrompt."

    - "**broadcast**:"
      "  - enableBroadcastPropagation, broadcastPropagationIntervalTicks."
      "  - broadcastMaxPropagationsPerTick, broadcastPropagationRange."
      "  - maxBroadcastsInPrompt, maxBroadcastsStored."
      "  - enableBroadcastYelling, broadcastYellingRange."

    - "**personality**:"
      "  - enablePersonalityArchetypes: Assign random personality to citizens."
      "  - customPersonalityArchetypes: List of custom personality definitions."

    - "**colony_diplomacy**:"
      "  - enableColonyDiplomacy."

    - "**memory**:"
      "  - memoryMode (MemoryMode — LIVE, FLASH): How memory compaction works."
      "  - enableMemoryCompaction: Periodically summarize and compress memories."
      "  - memoryCompactionIntervalTicks, memoryCompactionThreshold."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/config/McTalkingConfig.java (citizens category, all sub-groups)."
    - "src/main/java/me/sshcrack/mc_talking/config/ConversationMode.java"
    - "src/main/java/me/sshcrack/mc_talking/config/MemoryMode.java"
    - "src/main/java/me/sshcrack/mc_talking/config/PersonalityArchetype.java"
---

# Citizens Configuration

All configuration options related to citizen behavior.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
