#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

EXACT_FILES = {
    ".pre-commit-config.yaml",
    "build.forge.gradle.kts",
    "build.neoforge.gradle.kts",
    "gradle.properties",
    "settings.gradle.kts",
    "stonecutter.gradle.kts",
    "stonecutter.properties.toml",
    "scripts/check-client-smoke-required.sh",
    "scripts/client-smoke-fingerprint.py",
    "scripts/test-client-smoke.sh",
}
PREFIXES = (
    "build-logic/",
    "src/api/java/",
    "src/main/java/",
    "src/main/resources/",
)


def relevant(path: str) -> bool:
    return path in EXACT_FILES or path.startswith(PREFIXES)


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def tracked_paths(ref: str | None) -> list[str]:
    if ref is None:
        raw = git("ls-files", "-z")
    else:
        raw = git("ls-tree", "-r", "--name-only", "-z", ref)
    return [p.decode() for p in raw.split(b"\0") if p and relevant(p.decode())]


def worktree_entries() -> list[tuple[str, bytes]]:
    # Include both index- and HEAD-tracked paths so staged deletions with an unstaged
    # worktree copy cannot disappear from the worktree fingerprint.
    tracked = sorted(set(tracked_paths(None) + tracked_paths("HEAD")))
    untracked_raw = git("ls-files", "--others", "--exclude-standard", "-z")
    untracked = [p.decode() for p in untracked_raw.split(b"\0") if p and relevant(p.decode())]
    entries: list[tuple[str, bytes]] = []
    for path in sorted(set(tracked + untracked)):
        file_path = ROOT / path
        if file_path.is_file():
            entries.append((path, file_path.read_bytes()))
    return entries


def index_entries() -> list[tuple[str, bytes]]:
    entries: list[tuple[str, bytes]] = []
    for path in sorted(tracked_paths(None)):
        try:
            content = git("show", f":{path}")
        except subprocess.CalledProcessError:
            continue
        entries.append((path, content))
    return entries


def head_entries() -> list[tuple[str, bytes]]:
    entries: list[tuple[str, bytes]] = []
    for path in sorted(tracked_paths("HEAD")):
        entries.append((path, git("show", f"HEAD:{path}")))
    return entries


def fingerprint(entries: list[tuple[str, bytes]]) -> str:
    digest = hashlib.sha256()
    for path, content in entries:
        digest.update(path.encode())
        digest.update(b"\0")
        digest.update(hashlib.sha256(content).digest())
        digest.update(b"\0")
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="Fingerprint client-launch-relevant repository content")
    parser.add_argument("mode", choices=("worktree", "index", "head"))
    args = parser.parse_args()

    if args.mode == "worktree":
        entries = worktree_entries()
    elif args.mode == "index":
        entries = index_entries()
    else:
        entries = head_entries()
    print(fingerprint(entries))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
