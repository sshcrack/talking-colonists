#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

GEMINI_VERSION=$(sed -n 's/^deps\.gemini_live_lib_version=//p' gradle.properties | tail -1)
MOD_VERSION=$(sed -n 's/^mod\.version = "\([^"]*\)"/\1/p' stonecutter.properties.toml | tail -1)
if [[ -z "$GEMINI_VERSION" || -z "$MOD_VERSION" ]]; then
  echo "Could not read release versions from gradle.properties / stonecutter.properties.toml" >&2
  exit 2
fi
GEMINI_MAJOR=${GEMINI_VERSION%%.*}
if ! [[ "$GEMINI_MAJOR" =~ ^[0-9]+$ ]]; then
  echo "Gemini Live Library version must start with a numeric semantic-version major: $GEMINI_VERSION" >&2
  exit 2
fi
GEMINI_COMPAT_RANGE="[$GEMINI_VERSION,$((GEMINI_MAJOR + 1)).0.0)"

variants=(
  "1.21.1 neoforge"
  "1.20.1 forge"
)

missing=0
for variant in "${variants[@]}"; do
  read -r minecraft loader <<<"$variant"
  version="${GEMINI_VERSION}-${minecraft}-${loader}"
  base_url="https://maven.sshcrack.me/releases/me/sshcrack/gemini_live_lib/${version}/gemini_live_lib-${version}"
  pom_code=$(curl -sS -o "/tmp/gemini-live-${minecraft}-${loader}.pom" -w '%{http_code}' "${base_url}.pom" || true)
  jar_code=$(curl -sS -o "/tmp/gemini-live-${minecraft}-${loader}.jar" -w '%{http_code}' "${base_url}.jar" || true)
  if [[ "$pom_code" != "200" || "$jar_code" != "200" ]]; then
    echo "RELEASE BLOCKED: me.sshcrack:gemini_live_lib:${version} POM=${pom_code:-network-error} JAR=${jar_code:-network-error}" >&2
    missing=1
  else
    echo "Found published dependency POM and JAR for me.sshcrack:gemini_live_lib:${version}"
  fi
done

if (( missing != 0 )); then
  echo "Publish the configured Gemini Live Library artifacts before treating a composite/cache build as release-ready." >&2
  exit 2
fi

# Point the composite-build override at a path that cannot exist. --refresh-dependencies prevents a
# previously cached unpublished module from turning this into a false-positive release check.
missing_library_dir=$(mktemp -d)
trap 'rm -rf "$missing_library_dir"' EXIT
export GEMINI_LIVE_LIBRARY_DIR="$missing_library_dir/not-present"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/cache/gradle}"
export MOD_IS_RELEASE=true

# Prevent stale snapshot/release artifacts from satisfying the checks below.
rm -rf build/libs
./gradlew buildAndCollect --refresh-dependencies --no-daemon --max-workers=1

for variant in "${variants[@]}"; do
  read -r minecraft loader <<<"$variant"
  mod_jar=$(find build/libs -type f -name "mc_talking-${MOD_VERSION}-${loader}+${minecraft}.jar" -print -quit)
  api_jar=$(find build/libs -type f -name "mc_talking-api-${MOD_VERSION}-${loader}+${minecraft}.jar" -print -quit)

  if [[ -z "$mod_jar" || -z "$api_jar" ]]; then
    echo "Missing collected ${minecraft}-${loader} mod/API artifact under build/libs" >&2
    exit 3
  fi

  mod_listing=$(mktemp)
  api_listing=$(mktemp)
  jar tf "$mod_jar" > "$mod_listing"
  jar tf "$api_jar" > "$api_listing"
  grep -q 'me/sshcrack/mc_talking/api/TalkingColonistsApi.class' "$mod_listing"
  grep -q 'me/sshcrack/mc_talking/api/TalkingColonistsApi.class' "$api_listing"
  if grep -qE 'me/sshcrack/mc_talking/(ConversationManager|manager/|internal/|mixin/)' "$api_listing"; then
    echo "Developer API artifact leaked implementation classes: $api_jar" >&2
    rm -f "$mod_listing" "$api_listing"
    exit 3
  fi
  if grep -qE 'META-INF/(mods.toml|neoforge.mods.toml)|\.mixins\.json$' "$api_listing"; then
    echo "Developer API artifact contains installable mod metadata: $api_jar" >&2
    rm -f "$mod_listing" "$api_listing"
    exit 3
  fi
  rm -f "$mod_listing" "$api_listing"

  metadata_path="META-INF/mods.toml"
  if [[ "$loader" == "neoforge" ]]; then
    metadata_path="META-INF/neoforge.mods.toml"
  fi
  metadata_dir=$(mktemp -d)
  (cd "$metadata_dir" && jar xf "$ROOT/$mod_jar" "$metadata_path")
  metadata_file="$metadata_dir/$metadata_path"
  if ! grep -Fq 'modId = "gemini_live_lib"' "$metadata_file" \
      || ! grep -Fq "versionRange = \"$GEMINI_COMPAT_RANGE\"" "$metadata_file"; then
    echo "Mod metadata does not constrain Gemini Live Library to $GEMINI_COMPAT_RANGE: $mod_jar" >&2
    rm -rf "$metadata_dir"
    exit 3
  fi
  rm -rf "$metadata_dir"

  echo "Verified collected artifacts for ${minecraft}-${loader}:"
  echo "  mod: $mod_jar"
  echo "  api: $api_jar"
done

echo "Release dependency and artifact verification passed without a Gemini composite build."
