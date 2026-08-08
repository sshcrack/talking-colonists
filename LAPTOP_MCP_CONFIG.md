# Laptop MCP configuration

This repository's `.laptop-mcp.toml` defines the stable requirements for disposable Laptop MCP development sandboxes.

## Project choices

- Network access stays enabled because Gradle, Stonecutter, Forge/NeoForge and Maven dependencies are resolved from remote repositories.
- OpenJDK 21 is installed in the derived image. The currently targeted Minecraft versions compile for Java 17/21, and Java 21 covers both without relying on an ad-hoc toolchain download for normal development.
- The sandbox has 8 GiB available while Gradle itself remains capped by the repository's `org.gradle.jvmargs=-Xmx2G` setting.
- No CPU set is pinned by default so several agents can work concurrently.
- No secrets or `.env` are copied. Gemini/publishing credentials must never be placed in `.laptop-mcp.toml`.
- `../gemini-live-library` is intentionally not copied or mounted. The build already supports the published Gemini Live Library when that sibling checkout is absent; use that path for isolated/reproducible sandbox work.

## Fresh-sandbox workflow

1. Read the generated `LAPTOP_MCP_SANDBOX.md` entry file first.
2. Read `AGENTS.md` for the repository-specific build/test and Gemini free-tier constraints.
3. Use `./gradlew` and the checked-in wrapper; do not persist `.gradle`, `build`, `run`, or `run_server` through Laptop MCP.
4. Use `.env.template` only as documentation. Keep real API keys and publishing credentials outside commits and repository config.

After changing `.laptop-mcp.toml`, run `laptop-mcp doctor` from the source checkout and refresh the repository worker before creating new sandboxes.
