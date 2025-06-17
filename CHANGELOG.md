# Changelog
All notable changes to this project will be documented in this file.

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
