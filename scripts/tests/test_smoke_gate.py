#!/usr/bin/env python3
"""Checks which paths require the local client smoke marker (mixin-relevant set only).

Run: python3 scripts/tests/test_smoke_gate.py
"""
import importlib.util
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("fingerprint", ROOT / "scripts" / "client-smoke-fingerprint.py")
fingerprint = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fingerprint)

GATED = [
    "src/main/java/me/sshcrack/mc_talking/mixin/CitizenAIMixin.java",
    "src/main/resources/mc_talking.mixins.json",
    "src/main/resources/aw/1.20.1-forge.cfg",
    "build-logic/src/main/kotlin/Loader.kt",
    "build.neoforge.gradle.kts",
    "gradle.properties",
    "stonecutter.properties.toml",
    "scripts/test-client-smoke.sh",
]
NOT_GATED = [
    "src/main/java/me/sshcrack/mc_talking/manager/DefaultCitizenPromptProvider.java",
    "src/main/resources/assets/mc_talking/lang/en_us.json",
    "src/api/java/me/sshcrack/mc_talking/api/TalkingColonistsApi.java",
    "src/test/java/me/sshcrack/mc_talking/config/QuotaTrackerTest.java",
    "docs/addon-api.md",
    "roadmap/2.1/qol.md",
]

failures = [p for p in GATED if not fingerprint.relevant(p)]
failures += [p for p in NOT_GATED if fingerprint.relevant(p)]
for p in failures:
    print(f"FAIL: {p} -> relevant={fingerprint.relevant(p)}")
if failures:
    sys.exit(1)
print(f"ok: {len(GATED)} gated, {len(NOT_GATED)} not gated")
