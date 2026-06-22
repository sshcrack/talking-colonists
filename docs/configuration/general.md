---
title: General Settings
ai_instructions:
  goal: |
    Document the `general` config category and its sub-groups. Include config key,
    type, default, and description for each option.

  config_fields:
    - "**language** (String, default 'en-US'): The language code for speech recognition/synthesis."
    - "**respondInGroups** (boolean): Whether citizens respond to group chat or only direct conversations."
    - "**sendMumblingAndConversationsToChat** (boolean): Whether mumbling/conversation text appears in chat."
    - "**continueWorkDuringConversation** (boolean): Whether citizens continue working while talking."
    - "**maxConversationDistance** (double): Max distance for a conversation to stay active."
    - "**maxConcurrentAgents** (int, default 3): Max simultaneous player-citizen conversations."
    - "**maxConcurrentBackground** (int, default 3): Max background tasks (pregeneration, compaction)."
    - "**modality** (enum ModalityModes — TEXT, AUDIO, TEXT_AND_AUDIO): Response modality."
    - "**disabledTools** (list of String): Tool names to disable for the AI."
    - "**sendErrorsToPlayers** (boolean): Whether to send error messages to players in chat."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/config/McTalkingConfig.java (general category)."
    - "src/main/java/me/sshcrack/mc_talking/config/ModalityModes.java"
---

# General Settings

Core configuration options for mod behavior.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
