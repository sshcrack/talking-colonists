#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

TIMEOUT_SECONDS="${CLIENT_SMOKE_TIMEOUT_SECONDS:-240}"
METADATA_ONLY_ASSETS="${CLIENT_SMOKE_METADATA_ONLY_ASSETS:-0}"
CRASH_PATTERN='---- Minecraft Crash Report ----|Crash report saved to:|Reported exception thrown!|Minecraft crashed!|Exception in thread "Render thread"|ModLoadingException|Failed to create mod instance|Failed to wait for future Mod Construction|NoClassDefFoundError: me/sshcrack/mc_talking/|Failed to complete lifecycle event|Loading errors encountered|There was an error during the .* event phase|MC_TALKING_AUTOQUIT_FAILURE:'

# All dependencies may already be cached while a Maven host is unavailable.
GRADLE_NETWORK_ARGS=()
if [ "${CLIENT_SMOKE_OFFLINE:-0}" = "1" ]; then
    GRADLE_NETWORK_ARGS+=(--offline)
fi

DISPLAY_PREFIX=()
if [ -z "${DISPLAY:-}" ]; then
    if command -v xvfb-run >/dev/null 2>&1; then
        DISPLAY_PREFIX=(xvfb-run -a)
    else
        echo "ERROR: no DISPLAY is available and xvfb-run is not installed." >&2
        echo "The client smoke test requires a real graphical Minecraft client." >&2
        exit 1
    fi
fi

VERSIONS=$(grep 'match("[0-9]' settings.gradle.kts | sed 's/.*match("\(.*\)", "\(.*\)").*/\1-\2/')
if [ -z "$VERSIONS" ]; then
    echo "ERROR: no versions found in settings.gradle.kts" >&2
    exit 1
fi

cleanup_process_group() {
    local pid="$1"
    if kill -0 "$pid" 2>/dev/null; then
        kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
        for _ in 1 2 3 4 5; do
            kill -0 "$pid" 2>/dev/null || return 0
            sleep 1
        done
        kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
    fi
}

cleanup() {
    ./gradlew "${GRADLE_NETWORK_ARGS[@]}" "Refresh active project" >/dev/null 2>&1 || true
}

prepare_metadata_only_assets() {
    local version="$1"
    local minecraft_version="${version%%-*}"
    local gradle_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
    local asset_root="$gradle_home/caches/neoformruntime/assets"
    local manifest="$gradle_home/caches/neoformruntime/artifacts/minecraft_${minecraft_version}_version_manifest.json"
    local properties="$ROOT_DIR/versions/$version/build/moddev/minecraft_assets.properties"

    echo "[$version] metadata-only asset fallback enabled"
    ./gradlew "${GRADLE_NETWORK_ARGS[@]}" ":$version:createMinecraftArtifacts" \
        -Pmc_talking.devtools=true \
        --no-daemon \
        --max-workers=1 >/dev/null

    if [ ! -f "$manifest" ]; then
        echo "ERROR: expected Minecraft version manifest was not produced: $manifest" >&2
        return 1
    fi

    mkdir -p "$asset_root/indexes" "$(dirname "$properties")"
    python3 - "$manifest" "$asset_root" "$properties" <<'PY_ASSETS'
import hashlib
import json
import pathlib
import sys
import urllib.request

manifest_path = pathlib.Path(sys.argv[1])
asset_root = pathlib.Path(sys.argv[2])
properties_path = pathlib.Path(sys.argv[3])
manifest = json.loads(manifest_path.read_text())
asset = manifest["assetIndex"]
index_id = asset["id"]
url = asset["url"].replace("https://piston-meta.mojang.com", "https://launchermeta.mojang.com")
index_path = asset_root / "indexes" / f"{index_id}.json"

if not index_path.exists() or hashlib.sha1(index_path.read_bytes()).hexdigest() != asset["sha1"]:
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read()
    actual = hashlib.sha1(data).hexdigest()
    if actual != asset["sha1"]:
        raise SystemExit(f"asset index checksum mismatch: expected {asset['sha1']}, got {actual}")
    index_path.write_bytes(data)

properties_path.write_text(f"assets_root={asset_root}\nasset_index={index_id}\n")
PY_ASSETS
}
trap cleanup EXIT

INITIAL_INDEX_FINGERPRINT="$(python3 scripts/client-smoke-fingerprint.py index)"
INITIAL_WORKTREE_FINGERPRINT="$(python3 scripts/client-smoke-fingerprint.py worktree)"
if [ "$INITIAL_INDEX_FINGERPRINT" != "$INITIAL_WORKTREE_FINGERPRINT" ]; then
    echo "ERROR: launch-relevant worktree content differs from the Git index." >&2
    echo "Stage the exact launch-relevant tree before running client smoke." >&2
    exit 1
fi

./gradlew "${GRADLE_NETWORK_ARGS[@]}" "Refresh active project" -Pmc_talking.devtools=true --no-daemon --max-workers=1 >/dev/null 2>&1

rm -f /tmp/client-smoke-*.log
FAILED=0

echo "=== Client Launch Smoke Test ==="
echo "Versions: $VERSIONS"
echo "Timeout per version: ${TIMEOUT_SECONDS}s"
echo "Runs are serialized to avoid concurrent Minecraft/NeoForm downloads."
if [ "${#DISPLAY_PREFIX[@]}" -gt 0 ]; then
    echo "No DISPLAY detected; using xvfb-run for the real client."
fi
if [ "$METADATA_ONLY_ASSETS" = "1" ]; then
    echo "Metadata-only asset fallback is enabled; vanilla asset CDN downloads will be skipped."
fi
echo

for VERSION in $VERSIONS; do
    LOG_FILE=$(mktemp "/tmp/client-smoke-${VERSION}-XXXXXX.log")
    MC_LOG="$ROOT_DIR/versions/$VERSION/run/logs/latest.log"
    rm -f "$MC_LOG"
    rm -rf "$ROOT_DIR/versions/$VERSION/run/saves/MC_Talking_Smoke"

    EXTRA_GRADLE_ARGS=()
    if [ "$METADATA_ONLY_ASSETS" = "1" ]; then
        prepare_metadata_only_assets "$VERSION"
        EXTRA_GRADLE_ARGS+=("-x" ":$VERSION:downloadAssets")
    fi

    echo "[$VERSION] launching -> $LOG_FILE"
    setsid "${DISPLAY_PREFIX[@]}" ./gradlew "${GRADLE_NETWORK_ARGS[@]}" ":$VERSION:runClientAutoQuit" \
        "${EXTRA_GRADLE_ARGS[@]}" \
        -Pmc_talking.devtools=true \
        -Pmc_talking.forceCreateWorld=true \
        --no-daemon \
        --max-workers=1 >"$LOG_FILE" 2>&1 &
    PID=$!
    START=$SECONDS
    CRASHED=0
    TIMED_OUT=0

    while kill -0 "$PID" 2>/dev/null; do
        if { [ -f "$MC_LOG" ] && grep -Eq -- "$CRASH_PATTERN" "$MC_LOG"; } || grep -Eq -- "$CRASH_PATTERN" "$LOG_FILE"; then
            CRASHED=1
            echo "[$VERSION] crash/failure marker detected; terminating auto client" | tee -a "$LOG_FILE"
            cleanup_process_group "$PID"
            break
        fi
        if (( SECONDS - START >= TIMEOUT_SECONDS )); then
            TIMED_OUT=1
            echo "[$VERSION] timeout after ${TIMEOUT_SECONDS}s; terminating auto client" | tee -a "$LOG_FILE"
            cleanup_process_group "$PID"
            break
        fi
        sleep 1
    done

    set +e
    wait "$PID"
    STATUS=$?
    set -e

    SUCCESS_MARKER=0
    if { [ -f "$MC_LOG" ] && grep -q 'MC_TALKING_AUTOQUIT_SUCCESS:world' "$MC_LOG" && grep -q 'MC_TALKING_RUNTIME_SUCCESS:' "$MC_LOG"; } \
        || { grep -q 'MC_TALKING_AUTOQUIT_SUCCESS:world' "$LOG_FILE" && grep -q 'MC_TALKING_RUNTIME_SUCCESS:' "$LOG_FILE"; }; then
        SUCCESS_MARKER=1
    fi

    if [ "$CRASHED" -ne 0 ] || [ "$TIMED_OUT" -ne 0 ] || [ "$STATUS" -ne 0 ] || [ "$SUCCESS_MARKER" -ne 1 ]; then
        FAILED=1
        echo "[$VERSION] FAIL (exit=$STATUS crash=$CRASHED timeout=$TIMED_OUT success_marker=$SUCCESS_MARKER)"
        if [ -f "$MC_LOG" ]; then
            cp "$MC_LOG" "${LOG_FILE%.log}-minecraft.log"
        fi
        echo "  Gradle log: $LOG_FILE"
        [ -f "${LOG_FILE%.log}-minecraft.log" ] && echo "  Minecraft log: ${LOG_FILE%.log}-minecraft.log"
    else
        echo "[$VERSION] PASS"
        rm -rf "$ROOT_DIR/versions/$VERSION/run/saves/MC_Talking_Smoke"
    fi
    echo

done

if [ "$FAILED" -ne 0 ]; then
    echo "FAILED: one or more clients crashed, hung, exited unsuccessfully, or never entered a healthy world." >&2
    exit 1
fi

# Stonecutter's devtools switch temporarily rewrites the active source view. Restore
# the normal project before fingerprinting so the marker describes the code that will
# actually be committed rather than the generated devtools view used by the smoke run.
cleanup
trap - EXIT

FINAL_INDEX_FINGERPRINT="$(python3 scripts/client-smoke-fingerprint.py index)"
FINAL_WORKTREE_FINGERPRINT="$(python3 scripts/client-smoke-fingerprint.py worktree)"
if [ "$FINAL_INDEX_FINGERPRINT" != "$INITIAL_INDEX_FINGERPRINT" ] \
        || [ "$FINAL_WORKTREE_FINGERPRINT" != "$INITIAL_INDEX_FINGERPRINT" ]; then
    echo "ERROR: launch-relevant content changed during the smoke run; refusing to certify a different tree." >&2
    exit 1
fi
printf '%s\n' "$INITIAL_INDEX_FINGERPRINT" > .client-smoke-verified
echo "Created .client-smoke-verified ($(cat .client-smoke-verified))"
echo "All client launch smoke tests passed."
