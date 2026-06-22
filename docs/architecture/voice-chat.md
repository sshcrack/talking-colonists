---
title: Voice Chat Integration
ai_instructions:
  goal: |
    Document the Simple Voice Chat integration.

  content_sections:
    - "**Integration point**: McTalkingVoicechatPlugin implements the Simple Voice Chat API."
    - "**How voice flows**:"
      "  - Player microphone audio → Voice Chat plugin → ConversationManager →"
      "    GeminiWsClient (sent to Gemini API)."
      "  - Gemini API response audio → GeminiWsClient → Voice Chat plugin →"
      "    Player speakers."
    - "**Volume categories**:"
      "  - Direct player-to-citizen audio."
      "  - Citizen-to-citizen audio."
    - "**Whisper system**: Citizens can whisper (citizenVoiceWhisper config)."
    - "**Audio distance**: citizenVoiceDistance controls how far citizen audio carries."
    - "**Silence detection**: Voice activity/silence detection in the plugin."
    - "**Config keys**: citizenVoiceWhisper, citizenVoiceDistance."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/McTalkingVoicechatPlugin.java"
    - "src/main/java/me/sshcrack/mc_talking/manager/audio/AudioProvider.java"
    - "src/main/java/me/sshcrack/mc_talking/manager/audio/CitizenEntityAudioProvider.java"
---

# Voice Chat Integration

How Simple Voice Chat integrates with the mod.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
