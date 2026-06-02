#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
ARCHIVE_NAME="talking-colonists-source.zip"

if [ ! -d "$REPO_ROOT/build/archives" ]; then
    mkdir -p "$REPO_ROOT/build/archives"
fi
ARCHIVE_PATH="$REPO_ROOT/build/archives/$ARCHIVE_NAME"

cd "$REPO_ROOT"

git ls-files -c -o --exclude-standard | zip -@ "$ARCHIVE_PATH"

echo "Created $ARCHIVE_PATH"
