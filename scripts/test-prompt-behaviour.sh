#!/usr/bin/env bash
# Opt-in live check of prompt behaviour against Gemini (roadmap T5).
#
# Runs PromptBehaviourLiveTest: Portuguese citizen-to-citizen script, no complaint about a
# cavern-style home, and a tool call for a tool-answerable question. Uses the cheap Flash text
# model, one request per scenario, no retries.
#
# Key lookup (never printed): GEMINI_API_KEY, else `geminiApiKey` from the game config
# versions/<version>/run/config/yacl-mc_talking.json5 (the main checkout's, also from a git
# worktree). No key -> SKIP with exit 0.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
MAIN_DIR="$(cd "$(git rev-parse --path-format=absolute --git-common-dir)/.." && pwd)"
TEST_PROJECT="${PROMPT_BEHAVIOUR_PROJECT:-1.21.1-neoforge}"

read_config_key() {
    python3 - "$@" <<'PY'
import re, sys
for path in sys.argv[1:]:
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        continue
    match = re.search(r'"?geminiApiKey"?\s*:\s*"([^"]*)"', text)
    if match and match.group(1).strip():
        print(match.group(1).strip())
        break
PY
}

KEY="${GEMINI_API_KEY:-}"
KEY_SOURCE="GEMINI_API_KEY"
if [[ -z "$KEY" ]]; then
    KEY_SOURCE="game config"
    configs=()
    for dir in "$ROOT_DIR" "$MAIN_DIR"; do
        for version in 1.21.1-neoforge 1.20.1-forge; do
            configs+=("$dir/versions/$version/run/config/yacl-mc_talking.json5")
        done
    done
    KEY="$(read_config_key "${configs[@]}")"
fi

if [[ -z "$KEY" ]]; then
    echo "SKIP: no Gemini API key (set GEMINI_API_KEY or geminiApiKey in versions/<version>/run/config/yacl-mc_talking.json5)."
    exit 0
fi

echo "Running live prompt behaviour checks on :$TEST_PROJECT (key from $KEY_SOURCE)..."
status=0
MC_TALKING_PROMPT_BEHAVIOUR_KEY="$KEY" ./gradlew ":$TEST_PROJECT:test" \
    --tests 'me.sshcrack.mc_talking.conversations.PromptBehaviourLiveTest' --rerun -q "$@" || status=$?

python3 - "versions/$TEST_PROJECT/build/test-results/test/TEST-me.sshcrack.mc_talking.conversations.PromptBehaviourLiveTest.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
try:
    suite = ET.parse(sys.argv[1]).getroot()
except (OSError, ET.ParseError):
    print("No test report found; see the Gradle output above.")
    sys.exit(0)
for case in suite.iter("testcase"):
    name = case.get("name").rstrip("()")
    failure = case.find("failure")
    if failure is None:
        failure = case.find("error")
    skipped = case.find("skipped")
    if failure is not None:
        detail = (failure.get("message") or "").splitlines()
        print(f"FAIL  {name}: {detail[0] if detail else ''}")
    elif skipped is not None:
        print(f"SKIP  {name}: {skipped.get('message') or ''}")
    else:
        print(f"PASS  {name}")
PY
exit "$status"
