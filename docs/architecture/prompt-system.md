---
title: Prompt System
ai_instructions:
  goal: |
    Document the prompt system architecture — how citizen prompts are constructed,
    the SPI for customization, and the view layer.

  content_sections:
    - "**Prompt construction flow**:"
      "  - CitizenPromptViewFactory builds a CitizenPromptView for each conversation."
      "  - The view contains all context: citizen info, colony stats, memories,"
      "    recent events, rumors, broadcasts, personality, etc."
      "  - DefaultCitizenPromptProvider converts the view into the final prompt string."
    - "**Prompt SPI** (api/prompt package):"
      "  - CitizenPromptProvider interface — implement to customize prompt generation."
      "  - CitizenPromptService — registry for providers."
      "  - CitizenPromptView — data object passed to providers."
      "  - api/prompt/view sub-package — view components."
    - "**Prompt sections**:"
      "  - Citizen identity (name, job, personality)."
      "  - Colony status (happiness, resources, buildings)."
      "  - Recent memories and events."
      "  - Active rumors and broadcasts."
      "  - Current tasks and needs."
      "  - Relationship data."
    - "**Customization**: Users/S modpack authors can implement their own"
      "  CitizenPromptProvider via the SPI to modify prompts."

  source_references:
    - "src/main/java/me/sshcrack/mc_talking/api/prompt/"
    - "src/main/java/me/sshcrack/mc_talking/manager/DefaultCitizenPromptProvider.java"
    - "src/main/java/me/sshcrack/mc_talking/manager/CitizenPromptViewFactory.java"
    - "src/main/java/me/sshcrack/mc_talking/manager/AIStateDescriber.java"
---

# Prompt System

How citizen AI prompts are constructed and customized.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
