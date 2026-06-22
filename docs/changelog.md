# Changelog

All notable changes to this project are documented here. See [GitHub Releases](https://github.com/sshcrack/talking-colonists/releases) for full version history and downloads.

## [1.7.0] - 2026-06-13

First stable release of the 1.7.x cycle.

## [1.7.0-beta.6] - 2026-06-12

- Fix Forge crash
- Add pre-commit hook for mixin smoke test

## [1.7.0-beta.5] - 2026-06-09

- Use game ticks instead of wall-clock time for colony events

## [1.7.0-beta.4] - 2026-06-09

- Persist colony events
- Prevent duplicate audio when pregenerated greeting plays

## [1.7.0-beta.3] - 2026-06-08

- Rumor system, fulfillment handler, broadcast system
- Colony foundation tracking and age milestones
- Citizen AI context improvements (job-specific actions, current status, building descriptions)
- Proximity-based broadcast propagation
- Voice rumors and broadcast announcements when player is nearby
- Enhanced happiness modifier prompts

## [1.7.0-beta.2] - 2026-06-05

- Fix voice server crash on restart

## [1.7.0-beta.1] - 2026-06-05

- Fix citizen aborting conversation when urgent needs resolve
- Add CurseForge release support

## [1.7.0-alpha.1] - 2026-06-04

- Player and environmental awareness
- Auto-fallback for Flash+TTS to Live WebSockets
- Async audio generation for greetings and exclaims
- Suppress raid content on peaceful difficulty
- Colony lifecycle events with general event buffer
- Quest-aware dialogue with delivery mentions
- Colony diplomacy and statistics banter
- Stop citizen wandering during player conversations
- Make guards sound tough
- Debug commands restructured under `/talking_colonists`

## [1.6.1] - 2026-05-26

- Register network payloads for Forge
- Add refmaps to prevent crash

## [1.6.0] - 2026-05-18

- Migrate to Stonecutter template for multi-loader support
- Public interface for custom providers
- Add citizen-to-citizen conversations
- Basic citizen memory system (persistent, LLM-based)
- Multi-language support
- Proximity mumbling with seamless player takeover
- Migrate config to YACL
- Change voice pitch for children
- Add all MineColonies job support
- Entity audio channels for voice chat

## [1.3.4] - 2026-02-15

- Fixed compatibility with the newest MineColonies version

## [1.3.3] - 2025-12-15

- Fixed compatibility with the newest MineColonies version

## [1.3.1] - 2025-06-25

- Directly send a chat message if an error occurs (can be disabled in config)
- Add notice for Flash 2.5 supporting only one concurrent voice chat connection on free tier

## [1.3.0] - 2025-06-24

- Migrate WebSocket to GeminiLiveLibrary mod for more modularity

## [1.2.2] - 2025-06-22

- Fix crash when loading into world
- Fix silence duration, concurrent voice stream modification
- Various AI output and prompt fixes

## [1.2.1] - 2025-06-20

- Add crafting recipe for talking device in 1.20.1
- Add Mixin support
- Migrate codebase to Forge for 1.20.1
- Fix silence bug, nameplate rendering, docs image links

## [1.1.4] - 2025-06-17

- Migrate to NBT-based capability system for persistent data

## [1.1.3] - Previous version

- See commit history for previous changes
