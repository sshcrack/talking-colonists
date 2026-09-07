#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ "${1:-}" != "" && "${1:-}" != "--live" ]]; then
    echo "Usage: bash scripts/verify-local.sh [--live]" >&2
    exit 2
fi
./gradlew buildAndCollect :1.21.1-neoforge:test :1.20.1-forge:test --no-daemon --max-workers=1
bash scripts/test-gemini-websocket.sh offline
bash scripts/test-client-smoke.sh
if [[ "${1:-}" == "--live" ]]; then
    bash scripts/test-gemini-websocket.sh live
fi
