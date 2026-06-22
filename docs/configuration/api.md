---
title: API Settings
ai_instructions:
  goal: |
    Document the `api` config category. Include the config key, type, default value,
    and description for each option. Use a table format if possible.

  config_fields:
    - "**geminiApiKey** (String): The Google Gemini API key. Required. Get from https://aistudio.google.com/apikey."
    - "**currentAiModel** (enum AvailableAI — Flash3 or Flash2_5): The AI model to use for conversations."
      "  Flash3 = `gemini-3.1-flash-live-preview`, Flash2_5 = `gemini-2.5-flash-native-audio-preview-12-2025`."
      "  Flash2_5 is cheaper but supports only 1 concurrent connection on free tier."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/config/McTalkingConfig.java (api category)."
    - "src/main/java/me/sshcrack/mc_talking/config/AvailableAI.java"
---

# API Settings

Configuration options for the Gemini API connection.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
