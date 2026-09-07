# Changelog
All notable changes to this project will be documented in this file.

## [2.0.0] - 2026-09-07

### Breaking changes

- Introduces addon API generation 2 as the supported integration baseline. Legacy addon API methods, direct manager/client access, and compatibility shims are intentionally not retained; addon migrations use `me.sshcrack.mc_talking.api` and `docs/addon-migration.md`.
- Publishes the developer-only `me.sshcrack:mc_talking-api` artifact separately from the normal runtime mod. Players still install only Talking Colonists.

### Added

- Composable prompt contributors, authorized addon query/command tools, activity reservations, typed conversation lifecycle APIs, controlled multi-citizen turns, and optional bounded autonomous group discussion.
- Provenance-aware verified facts and memory writes, including durable addon-confirmed outcomes and lossless migration of legacy memory data.
- Meetings integration examples and compile-checked public API examples for both supported loaders.

### Reliability

- Token-owned foreground/background session lifecycle, bounded provider recovery, exact stale-callback protection, and deterministic shutdown cleanup.
- Audible playback drain, exact-turn cancellation, barge-in/stale-audio protection, pregenerated interruption, and non-overlapping paired Live turns.
- Model/backend-specific voice rejection recovery with stable fallback and bounded retries.
- Structured memory generation and strict response validation before persistent mutation.

### Dependencies

- Requires Gemini Live Library `>=2.4.0,<3.0.0`. The upper bound prevents a future breaking Gemini 3.x release from being accepted implicitly.

## [1.3.4] - 2026-02-15
- Fixed compatibility with the newest minecolonies version

## [1.3.3] - 2025-12-15
- Fixed compatibility with the newest minecolonies version

## [1.3.1] - 2025-06-25
-  Directly send a chat message if an error occurs (can be disabled in the config)
- Add notice for Flash2.5 supporting only one concurrent voice chat connection on free tier

## [1.3.0] - 2025-06-24
- Migrate Websocket to GeminiLiveLibrary mod for more modularity

## [1.2.2] - 2025-06-22
### Fixed
- Issue that caused crashing when loading into the world
- Lengthen the silence duration that is being sent
- Fix concurrent modification of the voice stream
- Add instruction to system prompt (no markdown anymore)
- Fixed AI Model not outputting text correctly
- Removed the text and audio functionality, it doesn't work properly with the API

## [1.2.1] - 2025-06-20
### Added
- Added crafting recipe for talking device in 1.20.1
- Added Mixin support for better integration with Minecraft
- Added PowerShell helper scripts for easier development workflow

### Fixed
- Fixed silence bug in voice chat integration
- Fixed nameplate rendering issues
- Fixed image links in documentation

### Changed
- Migrated codebase to Forge for 1.20.1
- Removed dedicated tracking manager class in favor of better architecture
- Updated server run directory for improved development experience
- Adjusted recipe format for better compatibility

### Technical
- Added MineColonies dependencies
- Disabled remapping for MineColonies mixins to prevent conflicts
- Updated build configurations

## [1.1.4] - 2025-06-17
### Changed
- Migrated from using Forge's data components to NBT-based capability system for storing persistent data.
- Improved data persistence across game sessions and server restarts.

### Technical
- Added `EntityDataProvider` capability for storing entity-specific data.
- Deprecated `ModAttachmentTypes` in favor of capability system.
- Added event handling for capability attachment and persistence.

## [1.1.3] - Previous version
- See commit history for previous changes.
