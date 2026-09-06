# 12 — Addon migration and release verification

Dependencies: 01–11.

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Review a pinned Colonist Errands source revision and inventory every hook into Talking
Colonists: mixins, reflection, registry access, client access, and prompt replacement.
For each, map to a core fix, a supported extension, or a remaining gap with rationale.
Inspect source and license attribution before incorporating code; prefer focused
behavioral fixes and preserve required notices for copied material.

Provide migration examples for tools, factual contributions, confirmed memory events,
and sessions, plus the Meetings integration guide from task 07. Compile examples
against public interfaces. Document compatibility/deprecation and required library
versions; avoid declaring compatibility with an untested addon release. Prepare
maintainer-facing release notes and a draft response explaining the new integration
surface, without sending messages or publishing releases.

Verify release artifacts without relying on an unpublished composite dependency.
If the required library artifact is not available, document that exact release
blocker and retain the distinction between composite success and release readiness.

## Acceptance

- Every observed Errands internal hook has an explicit migration disposition.
- Public examples compile for both loaders; tests cover simultaneous contributors
  and tools from two addons, session cleanup, permissions, and old-save loading.
- Record in-world validation for player dialogue, paired dialogue, controlled group
  turns, interruption, disconnect, and shutdown; run mixin smoke checks if applicable.
- Both distributable mod artifacts and their library requirements are verified.
- Release notes state implemented behavior, compatibility limits, and remaining
  manual checks. Roadmap statuses match evidence rather than planned outcomes.
