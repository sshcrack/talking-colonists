#!/bin/bash
set -euo pipefail

STAGED=$(git diff --cached --name-only -- 'src/main/java/me/sshcrack/mc_talking/mixin/' 'src/main/resources/**/*.mixins.json')
if [ -z "$STAGED" ]; then
    exit 0
fi

if [ ! -f .mixin-smoke-verified ]; then
    echo "!!! Mixin files changed but .mixin-smoke-verified not found !!!" >&2
    echo "  Run: bash scripts/test-mixin-smoke.sh" >&2
    echo "  Then stage and commit the generated .mixin-smoke-verified file." >&2
    exit 1
fi

VERIFIED=$(tr -d ' \n' < .mixin-smoke-verified)
if [ -z "$VERIFIED" ]; then
    echo "!!! .mixin-smoke-verified is empty !!!" >&2
    echo "  Run: bash scripts/test-mixin-smoke.sh" >&2
    exit 1
fi

HEAD_HASH=$(git rev-parse HEAD)

if [ "$VERIFIED" != "$HEAD_HASH" ]; then
    echo "!!! Mixin smoke test is stale !!!" >&2
    echo "  .mixin-smoke-verified points to $VERIFIED" >&2
    echo "  Current HEAD is $HEAD_HASH" >&2
    echo "  Re-run: bash scripts/test-mixin-smoke.sh" >&2
    echo "  Then stage and commit the generated .mixin-smoke-verified file." >&2
    exit 1
fi
