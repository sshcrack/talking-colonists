#!/usr/bin/env bash
set -euo pipefail

PROPS_FILE="stonecutter.properties.toml"
TARGET_DIR="build/libs"

echo "Building project..."
./gradlew build

VERSION=$(
    grep '^mod\.version' "$PROPS_FILE" \
    | sed -E 's/.*"([^"]+)".*/\1/'
)

CHANNEL_TAG=$(
    grep '^mod\.channel_tag' "$PROPS_FILE" \
    | sed -E 's/.*"([^"]+)".*/\1/'
)

PATTERN="mc_talking-${VERSION}${CHANNEL_TAG}-*-SNAPSHOT.jar"

echo "Looking for JARs matching: $PATTERN"

rm -rf "$TARGET_DIR"
mkdir -p "$TARGET_DIR"

find versions -type f -path '*/build/libs/*.jar' | while read -r jar; do
    filename="$(basename "$jar")"

    if [[ "$filename" == $PATTERN ]]; then
        cp "$jar" "$TARGET_DIR/"
        echo "Copied: $filename"
    fi
done

echo "Finished. Collected JARs into $TARGET_DIR/"
