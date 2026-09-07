# Tests, build, and release reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Review whether the diff has trustworthy validation and can be built/published with the
intended artifacts. Primary areas are `src/test/`, `src/apiTest/`, Gradle/build logic,
Stonecutter config, scripts, GitHub workflows, version metadata, changelog/release
readiness docs, and the public API artifact wiring.

## Review questions

- Do changed behaviors have regression tests at the correct boundary, especially
  lifecycle races, API contracts, memory parsing/provenance, tool authorization, and
  audio ordering?
- Are tests deterministic, asserting observable behavior rather than implementation
  details, and free from sleeps/network/Gemini credentials where a fake or controlled
  executor should be used?
- Do addon API compile/contract tests truly compile against the published API surface
  instead of inheriting `src/main` internals through the test classpath?
- Are both `1.21.1-neoforge` and `1.20.1-forge` included in build/collection/release
  paths, with Stonecutter conditionals and generated artifacts wired consistently?
- Do runtime/API/resource/build changes have fresh required client-smoke evidence, and
  is `.client-smoke-verified` consistent with the repository's fingerprint contract?
- Do version numbers, dependency versions, publication metadata, migration docs,
  changelog, and release-readiness records agree about breaking vs non-breaking
  changes and the artifact users/addon developers should consume?
- Does the main mod remain a single install for normal users while addon developers
  can depend on the separate API artifact as intended?
- Could Gradle source-set/publication wiring accidentally omit API classes, include
  internal classes, duplicate classes between artifacts, or publish against a local
  composite dependency unintentionally?

## Execution ownership

By default inspect validation evidence without launching heavy tasks. If the parent
explicitly designates you as the one validation runner, run Gradle work serially using
the repository guidance and record exact commands/results. For launch-relevant
changes, the required client-smoke test is the decisive runtime check; a successful
compile alone is not equivalent evidence.

Report missing tests/validation only when the changed behavior creates a concrete
regression risk or violates an explicit repository/release requirement. Avoid generic
"more tests would be nice" findings.
