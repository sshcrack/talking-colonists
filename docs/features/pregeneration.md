---
title: Audio Pregeneration
ai_instructions:
  goal: |
    Document the audio pregeneration system for reducing latency.

  content_sections:
    - "**What it is**: Pre-generate common audio responses (greetings, threat responses)"
      "  so they play instantly instead of waiting for the AI."
    - "**How it works**:"
      "  - PregenerationTaskService runs background tasks to generate audio."
      "  - PregenerationGeminiClient handles the API calls."
      "  - Generated audio is cached per citizen."
      "  - DeliveryInteractionManager manages when pregenerated audio is played."
    - "**Heatmap system**: Tracks where players spend time (PlayerHeatmapTracker)"
      "  to prioritize pregeneration for frequently visited areas."
    - "**Config keys**: enablePregeneration, pregeneratedGreetingDistance,"
      "  threatPlayCooldownMs, maxPregeneratedGreetingsPerCitizen,"
      "  maxGreetingsPerTickInterval, enablePlayerGreetingPregen,"
      "  playerGreetingDistance."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/pregen/"
---

# Audio Pregeneration

Pre-generated audio for faster response times.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
