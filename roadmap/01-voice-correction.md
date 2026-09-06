# 01 — Correct deterministic voice selection

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Inspect `config/AvailableAI.java`. Correct `Archid` to `Achird` in both model lists
without changing list order, length, or UUID selection behavior. Confirm the selected
models' voice support against official documentation. Keep this fix independent of
the broader recovery work in task 10.

## Acceptance

- Both misspelled entries are corrected.
- A representative UUID previously selecting the entry now selects `Achird`.
- UUIDs selecting other entries retain their original voices in both model choices.
- Both loaders build. Record any model support uncertainty separately from the typo fix.

## Implementation record — 2026-09-06

- Corrected `Archid` to `Achird` in both `AvailableAI.Flash3` and
  `AvailableAI.Flash2_5` male voice lists without changing list order or length.
- Added `AvailableAITest` covering UUID
  `00000000-0000-0000-0000-000000000019`, which deterministically selects the
  corrected slot, plus UUID `00000000-0000-0000-0000-000000000000` to verify an
  unaffected `Puck` selection remains stable for both models.
- Added shared JUnit 5 wiring to both loader builds using the same JUnit BOM /
  launcher pattern as ModDevGradle's own test project. Both loader test source sets
  receive Minecraft/modding dependencies through ModDevGradle's
  `addModdingDependenciesTo(sourceSets["test"])` API rather than manual classpath
  mutation. The NeoForge `unitTest` launch integration was evaluated, but it performs
  a full FML/Minecraft bootstrap and asset download; it is unnecessary for this
  deterministic test and the sandbox could not complete the Mojang asset TLS handshake.
- Verified against current official Google documentation that `Achird` is a supported
  prebuilt voice, and that native-audio Live models use the available TTS voices. The
  configured `gemini-3.1-flash-live-preview` and
  `gemini-2.5-flash-native-audio-preview-12-2025` model IDs are both currently listed
  as Live API models.
- Validation: `./gradlew test --no-daemon` passed for both `1.20.1-forge` and
  `1.21.1-neoforge`; `./gradlew buildAndCollect --no-daemon` passed and produced both
  loader artifacts. Existing Javadoc warnings remain unrelated to this task.
- No mixins, configuration values, or Gemini Live Library sources changed; no manual
  checks remain for this roadmap item.
