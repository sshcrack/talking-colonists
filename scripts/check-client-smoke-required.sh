#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# The mixin-relevant file set (mixin classes, mixin configs, access transformers/wideners,
# build logic, loader build scripts, and the smoke scripts themselves) is the single source
# of truth in scripts/client-smoke-fingerprint.py's relevant() function. Keep this check in
# sync with that filter rather than duplicating pathspecs here.
changed=$(python3 scripts/client-smoke-fingerprint.py changed)

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
