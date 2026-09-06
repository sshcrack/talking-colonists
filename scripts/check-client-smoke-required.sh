#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

changed=$(git diff --cached --name-only -- \
    'src/main/java/**' \
    'src/main/resources/**' \
    'src/api/java/**' \
    'build-logic/**' \
    'build.forge.gradle.kts' \
    'build.neoforge.gradle.kts' \
    'settings.gradle.kts' \
    'stonecutter.gradle.kts' \
    'stonecutter.properties.toml' \
    'gradle.properties' \
    '.pre-commit-config.yaml' \
    'scripts/test-client-smoke.sh' \
    'scripts/check-client-smoke-required.sh' \
    'scripts/client-smoke-fingerprint.py')

if [ -z "$changed" ]; then
    exit 0
fi

if ! git diff --cached --name-only -- .client-smoke-verified | grep -q .; then
    echo "!!! Client launch-relevant files changed but .client-smoke-verified is not staged !!!" >&2
    echo "  Stage the intended changes, run: bash scripts/test-client-smoke.sh" >&2
    echo "  Then stage .client-smoke-verified and commit." >&2
    exit 1
fi

verified=$(git show :.client-smoke-verified 2>/dev/null | tr -d ' \n')
if [ -z "$verified" ]; then
    echo "!!! .client-smoke-verified is missing or empty in the index !!!" >&2
    exit 1
fi

expected=$(python3 scripts/client-smoke-fingerprint.py index)
if [ "$verified" != "$expected" ]; then
    echo "!!! Client launch smoke verification is stale !!!" >&2
    echo "  verified: $verified" >&2
    echo "  staged:   $expected" >&2
    echo "  Stage the intended launch-relevant changes, re-run:" >&2
    echo "    bash scripts/test-client-smoke.sh" >&2
    echo "  Then stage .client-smoke-verified." >&2
    exit 1
fi
