---
title: Contributing
ai_instructions:
  goal: |
    Document the contribution workflow, PR guidelines, and coding conventions.

  content_sections:
    - "**PR workflow**:"
      "  - Fork the repo, create a feature branch."
      "  - Make changes, ensuring they work on all supported versions."
      "  - Run mixin smoke test if mixins were modified."
      "  - Commit .mixin-smoke-verified if smoke test passed."
      "  - Open a PR against the main branch."
    - "**Mixin guidelines**:"
      "  - ONLY put mixins in the mixin package."
      "  - EVERY class in the mixin package MUST be a mixin or accessor."
      "  - Run `bash scripts/test-mixin-smoke.sh` after mixin changes."
    - "**Code style**:"
      "  - 4-space indent for Java, 2-space for JSON/YAML/Markdown."
      "  - Single class imports (import-on-demand threshold = 999)."
      "  - No automated formatter — match existing style."
    - "**Config changes**:"
      "  - When adding/removing config values in McTalkingConfig,"
      "    update `src/main/resources/assets/mc_talking/lang/en_us.json`."
    - "**Stonecutter notes**:"
      "  - Never commit changes to .sc_active_version (pre-commit hook blocks it)."
      "  - Use `--no-verify` only if intentional."

  source_references:
    - "AGENTS.md (Code Style section, Mixin Smoke Test section)."
    - ".pre-commit-config.yaml"
---

# Contributing

Guidelines for contributing to MineColonies Talking Citizens.

> **Note**: This is a stub page. Content should be populated per the `ai_instructions` above.
