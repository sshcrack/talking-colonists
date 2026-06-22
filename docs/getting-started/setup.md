---
title: Setup
ai_instructions:
  goal: |
    Guide the user through installing and configuring the mod from scratch.

  content_sections:
    - "**Dependencies** — List required mods with links:"
      "  - MineColonies (CurseForge)"
      "  - Simple Voice Chat (Modrinth)"
      "  - Gemini Live Lib (CurseForge)"
      "  - Required Minecraft versions: 1.20.1 (Forge) or 1.21.1 (NeoForge)."
    - "**API Key Setup** — Visit Google AI Studio, create an API key, copy the token."
      "  Include the screenshots referenced in README.md."
    - "**Configuration** — Two methods:"
      "  - In-game: Mod menu → Talking Citizens → Config (YACL GUI)."
      "  - Direct file edit: `config/yacl-mc_talking.json5`."
      "  Show a minimal config example with the API key and language set."
    - "**Language** — Set your speaking language. Link to Google's supported languages."
    - "**Verification** — How to confirm the mod loaded successfully (check logs for "
      "'McTalking initialized', check config file exists)."
    - "**Free Tier Notes** — Free tier allows up to 3 concurrent citizens. "
      "Flash 2.5 model supports only 1 concurrent voice connection."

  source_references:
    - "README.md lines 3-15 (setup guide)."
    - "src/main/java/me/sshcrack/mc_talking/config/McTalkingConfig.java — for config field names."
    - "Config file: config/yacl-mc_talking.json5."
---

# Setup

Instructions for installing and configuring MineColonies Talking Citizens.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
