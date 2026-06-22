# Contributing

## PR Workflow

1. Fork the repository and create a feature branch.
2. Make changes, ensuring they work on all supported versions (1.20.1 Forge + 1.21.1 NeoForge).
3. If you modified mixins, run the mixin smoke test.
4. Commit the generated `.mixin-smoke-verified` file if the smoke test passes.
5. Open a PR against the `main` branch.

## Mixin Guidelines

- ONLY put mixins in the `src/main/java/me/sshcrack/mc_talking/mixin` package.
- EVERY class in the `mixin` package MUST be a mixin or accessor.
- After modifying mixins, run: `bash scripts/test-mixin-smoke.sh`

## Code Style

- **Indentation**: 4 spaces for Java, 2 spaces for JSON/YAML/Markdown (`.editorconfig`).
- **Imports**: Single class imports; import-on-demand threshold = 999.
- **No automated formatter** — match the existing style of the codebase.

## Config Changes

When adding or removing configuration values in `McTalkingConfig`:

1. Update the config class with proper YACL annotations.
2. Update `src/main/resources/assets/mc_talking/lang/en_us.json` with translation keys.

## Stonecutter Notes

- Never commit changes to `.sc_active_version` — the pre-commit hook blocks it.
- Use `git commit --no-verify` only if the change is intentional.

## Pre-commit Hooks

The project uses pre-commit hooks. The `check-mixin-smoke-required` hook blocks commits if mixin files are staged but `.mixin-smoke-verified` doesn't match HEAD. Run the smoke test and stage the generated file, then commit.

## Dependencies

| Dependency | Version (Forge 1.20.1) | Version (NeoForge 1.21.1) |
|------------|------------------------|---------------------------|
| MineColonies | 1.20.1-1.1.1218-snapshot | 1.1.1305-1.21.1-snapshot |
| Structurize | 1.20.1-1.0.806-snapshot | 1.0.823-1.21.1-snapshot |
| BlockUI | 1.20.1-1.0.190-snapshot | 1.0.211-1.21.1-snapshot |
| Domum Ornamentum | 1.20.1-1.0.288-snapshot | 1.0.233-snapshot |
| Gemini Live Lib | 2.3.0-1.20.1-forge | 2.3.0-1.21.1-neoforge |
