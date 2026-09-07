#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBRARY_DIR="${GEMINI_LIVE_LIBRARY_DIR:-$ROOT_DIR/../gemini-live-library}"
MODE="${1:-offline}"

if [[ ! -f "$LIBRARY_DIR/gradlew" ]]; then
    echo "Gemini Live Library checkout missing; set GEMINI_LIVE_LIBRARY_DIR." >&2
    exit 1
fi

case "$MODE" in
    offline)
        # Never inherit credentials into an offline release check.
        unset GEMINI_API_KEY GEMINI_LIVE_TEST_CONFIG GEMINI_LIVE_TEST_KEY_FILE
        cd "$LIBRARY_DIR"
        failed=0
        for version in 1.21.1-neoforge 1.20.1-forge; do
            ./gradlew ":$version:test" --rerun --no-daemon --max-workers=1 || failed=1
        done
        exit "$failed"
        ;;
    live)
        # Explicit opt-in, one model/session only, no retry loop. The Java test reads the
        # credential directly; neither this script nor Gradle receives it in an argument.
        if [[ -z "${GEMINI_API_KEY:-}" && -z "${GEMINI_LIVE_TEST_CONFIG:-}" && -z "${GEMINI_LIVE_TEST_KEY_FILE:-}" ]]; then
            if [[ -f /tmp/gemini-live-key.key ]]; then
                export GEMINI_LIVE_TEST_KEY_FILE=/tmp/gemini-live-key.key
            else
                export GEMINI_LIVE_TEST_CONFIG="$ROOT_DIR/versions/1.21.1-neoforge/run/config/yacl-mc_talking.json5"
            fi
        fi
        if [[ -n "${GEMINI_LIVE_TEST_CONFIG:-}" && ! -f "$GEMINI_LIVE_TEST_CONFIG" ]]; then
            echo "Test config missing; set GEMINI_LIVE_TEST_CONFIG or GEMINI_API_KEY." >&2
            exit 1
        fi
        if [[ -n "${GEMINI_LIVE_TEST_CONFIG:-}" ]]; then
            export GEMINI_LIVE_TEST_CONFIG="$(realpath "$GEMINI_LIVE_TEST_CONFIG")"
        fi
        if [[ -n "${GEMINI_LIVE_TEST_KEY_FILE:-}" ]]; then
            export GEMINI_LIVE_TEST_KEY_FILE="$(realpath "$GEMINI_LIVE_TEST_KEY_FILE")"
        fi
        cd "$LIBRARY_DIR"
        exec ./gradlew :1.21.1-neoforge:test --tests '*GeminiLiveIntegrationTest' \
            --rerun --no-daemon --max-workers=1
        ;;
    *)
        echo "Usage: bash scripts/test-gemini-websocket.sh [offline|live]" >&2
        exit 2
        ;;
esac
