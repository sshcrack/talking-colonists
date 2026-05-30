#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

ACTIVE_VERSION=$(cat "$PROJECT_DIR/.sc_active_version" | tr -d '[:space:]')
MC_VERSION="${ACTIVE_VERSION%%-*}"
LOADER="${ACTIVE_VERSION#*-}"

PROPS_FILE="$PROJECT_DIR/stonecutter.properties.toml"

MINECOLONIES_VERSION=$(sed -n "/^\[$LOADER\.\"$MC_VERSION\"\]/,/^\[/p" "$PROPS_FILE" \
  | grep "^deps.minecolonies_version" \
  | head -1 \
  | sed 's/.*=\s*"\(.*\)"/\1/')

if [ -z "$MINECOLONIES_VERSION" ]; then
  echo "Could not find minecolonies version for $LOADER/$MC_VERSION" >&2
  exit 1
fi

CACHE_DIR="$HOME/.gradle/caches/modules-2/files-2.1/com.ldtteam/minecolonies/$MINECOLONIES_VERSION"

if [ ! -d "$CACHE_DIR" ]; then
  echo "MineColonies not found in Gradle cache at $CACHE_DIR" >&2
  exit 1
fi

SOURCES_JAR=$(find "$CACHE_DIR" -name "*-sources.jar" -maxdepth 3 2>/dev/null | head -1)

if [ -z "$SOURCES_JAR" ]; then
  echo "No sources jar found for minecolonies $MINECOLONIES_VERSION" >&2
  exit 1
fi

echo "$SOURCES_JAR"
