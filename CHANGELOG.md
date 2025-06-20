# Changelog
All notable changes to this project will be documented in this file.

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
