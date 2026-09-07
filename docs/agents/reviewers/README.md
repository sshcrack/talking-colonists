# Review subagent prompts

These prompts split a change review into independent lenses so one reviewer does not
anchor the others. Give each selected prompt to a separate review-only agent and
replace `{{FIXED_POINT}}` with the commit, tag, branch, or merge-base target that all
reviewers should compare against.

Every specialist prompt points at [`COMMON.md`](COMMON.md). That file is the shared
review contract and is intentionally not duplicated into every prompt.

## Recommended fan-out

For a broad release/branch review, run these in parallel:

- [`01-spec-and-roadmap.md`](01-spec-and-roadmap.md) — requested behavior vs implementation.
- [`02-standards-and-architecture.md`](02-standards-and-architecture.md) — repository standards and structural smells.
- [`03-addon-api.md`](03-addon-api.md) — public addon API boundary and compatibility.
- [`04-lifecycle-and-concurrency.md`](04-lifecycle-and-concurrency.md) — ownership, state machines, cancellation, races.
- [`05-minecraft-integration.md`](05-minecraft-integration.md) — MineColonies, mixins, loaders, Stonecutter, sides.
- [`06-gemini-and-audio.md`](06-gemini-and-audio.md) — Gemini transport, voice, audio resources and recovery.
- [`07-memory-prompts-and-tools.md`](07-memory-prompts-and-tools.md) — factual context, memory, prompt composition, tool authorization.

Use [`08-tests-build-and-release.md`](08-tests-build-and-release.md) as the validation
reviewer. It may inspect test/build wiring in parallel, but only one agent should run
heavy Gradle/client-smoke validation at a time because this repository deliberately
serializes Minecraft/NeoForm work.

The first two prompts preserve the two independent axes from the `code-review` skill:
**Standards** and **Spec**. Keep their findings separate when aggregating. Domain
reviewers add correctness lenses; they do not replace either axis.

## Aggregation rule

Deduplicate identical findings only after all reviewers return. Preserve the original
reviewer/lens on each finding and do not silently lower severity because another lens
passed. A standards pass does not prove spec correctness, and a spec pass does not
prove runtime correctness.
