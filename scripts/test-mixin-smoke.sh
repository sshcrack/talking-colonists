#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== Mixin Smoke Test ==="
echo ""

# Clean up stale temp files from previous runs
rm -f /tmp/mixin-smoke-*.log

# Discover all version-loader pairs from settings.gradle.kts
VERSIONS=$(grep 'match("[0-9]' settings.gradle.kts | sed 's/.*match("\(.*\)", "\(.*\)").*/\1-\2/')
if [ -z "$VERSIONS" ]; then
    echo "ERROR: no versions found in settings.gradle.kts" >&2
    exit 1
fi

echo "Versions under test:"
for VERSION in $VERSIONS; do
    echo "  - $VERSION"
done
echo ""

cleanup() {
    ./gradlew "Refresh active project" > /dev/null 2>&1 || true
}

trap cleanup EXIT

./gradlew "Refresh active project" -Pmc_talking.devtools=true  > /dev/null 2>&1

# Run each version in parallel, capture output to temp files
declare -a LOG_FILES
declare -a PIDS
declare -a VERSION_LIST

I=0
for VERSION in $VERSIONS; do
    LOG_FILE=$(mktemp "/tmp/mixin-smoke-${VERSION}-XXXXXX.log")
    LOG_FILES[$I]="$LOG_FILE"
    VERSION_LIST[$I]="$VERSION"

    (
        echo "[$(date +%H:%M:%S)] === Starting mixin smoke test for $VERSION ==="
        echo ""
        cd "$ROOT_DIR"

        if ./gradlew ":$VERSION:runClientAutoQuit" -Pmc_talking.devtools=true --no-daemon 2>&1; then
            echo ""
            echo "[$(date +%H:%M:%S)] === BUILD SUCCESSFUL: $VERSION ==="
        else
            echo ""
            echo "[$(date +%H:%M:%S)] === BUILD FAILED: $VERSION ==="
        fi
    ) > "$LOG_FILE" 2>&1 &

    PIDS[$I]=$!
    echo "  Spawned: $VERSION (PID $!) -> $LOG_FILE"
    I=$((I+1))
done

echo ""
echo "Waiting for all builds to complete..."
echo ""

FAILED=0
for I in "${!PIDS[@]}"; do
    wait "${PIDS[$I]}" || FAILED=1
done

echo ""
echo "=========================================="
echo "=== Mixin Smoke Test Results            ==="
echo "=========================================="
echo ""

for I in "${!VERSION_LIST[@]}"; do
    VERSION="${VERSION_LIST[$I]}"
    LOG_FILE="${LOG_FILES[$I]}"

    # Determine status
    if grep -q "BUILD SUCCESSFUL" "$LOG_FILE" 2>/dev/null; then
        STATUS="PASS"
    else
        STATUS="FAIL"
    fi

    printf "  [%-4s] %-18s %s\n" "$STATUS" "$VERSION" "$LOG_FILE"
done

echo ""

# Also copy latest Minecraft logs alongside the gradle output for easier debugging
for I in "${!VERSION_LIST[@]}"; do
    VERSION="${VERSION_LIST[$I]}"
    LOG_FILE="${LOG_FILES[$I]}"
    MC_LOG="versions/$VERSION/run/logs/latest.log"

    if [ -f "$MC_LOG" ]; then
        cp "$MC_LOG" "${LOG_FILE%.log}-minecraft.log"
    fi
done

if [ "$FAILED" -ne 0 ]; then
    echo "FAILED: one or more mixin smoke tests did not pass."
    exit 1
fi

echo "All mixin smoke tests passed."
