# Addon API generation 2 release readiness

This document records maintainer-facing release evidence for the addon integration surface. It is
not a compatibility claim for any external addon release.

## Compatibility baseline

Talking Colonists `2.0.0-beta.1` exposes addon API generation `2` (`TalkingColonistsApi.API_MAJOR_VERSION`).
The supported game/loader targets in this repository are:

| Minecraft | Loader baseline | MineColonies compile baseline | Addon API artifact coordinate |
| --- | --- | --- | --- |
| `1.21.1` | NeoForge `21.1.213` | `1.1.1305-1.21.1-snapshot` | `me.sshcrack:mc_talking-api:2.0.0-beta.1-1.21.1-neoforge` |
| `1.20.1` | Forge `47.2.0` | `1.20.1-1.1.1218-snapshot` | `me.sshcrack:mc_talking-api:2.0.0-beta.1-1.20.1-forge` |

The normal Talking Colonists runtime currently requires Gemini Live Library `>=2.4.1,<3.0.0` through
its loader metadata. The upper bound is intentional: a future Gemini Live Library 3.x release may
contain breaking API changes and must not be accepted implicitly. The minimum was raised from the
originally targeted `2.4.0` to `2.4.1` because `2.4.0` was never published to the public Maven
repository (see "Release artifact verification" below); `2.4.1` is the first published release in
that line and is a superset-compatible successor. Players install only the normal Talking Colonists
mod; `mc_talking-api` is a developer compile artifact and must not be packaged or installed as a
second mod.

API generation 2 is an intentional breaking addon baseline. Compatibility is only claimed for code
that compiles against the matching `mc_talking-api` artifact and uses `me.sshcrack.mc_talking.api` as
its Talking Colonists boundary. No legacy API adapters are provided.

## External addon compatibility

Colonist Errands `3.0.0-alpha.3` has been externally tested against Talking Colonists
`2.0.0-beta.1`. The addon binary was built against `2.0.0-alpha.2` and loaded unchanged against
`beta.1`; all 42 addon tools registered, controlled turns authenticated a real player through
`addPlayerStatement(...)`, player-scoped tools completed without `unauthorized`, and real colony data
was returned through the provider. The same testing also identified the detached `FakePlayer` harness
case hardened in this beta: loader-provided fake players are valid controlled test actors even though
they are absent from `PlayerList`.

This is useful binary/runtime compatibility evidence, but it does not replace this repository's own
API-jar compile checks or loader smoke tests. Voyager was previously reviewed at
`6666432ec94e58089635dfe8ea3cc6967b59f12f`; its Talking Colonists integration at that point was
mediated through Colonist Errands. No public Colony Meetings source was available for compatibility
testing; the Meetings documentation remains an integration contract/example rather than a tested
third-party release claim.

## Automated evidence

The public examples cover tools, prompt facts, confirmed memory outcomes, activity reservations,
ordinary sessions, and controlled meeting turns. `compileAddonApiExamples` compiles those examples
against the stripped developer JAR rather than the full implementation output. `verifyApiJar` checks
that implementation classes and mod metadata do not leak into that developer artifact.

The regression suite additionally covers:

- deterministic simultaneous prompt contributors and tools from two independent addon namespaces,
  including independent unregister/cleanup;
- current permission rechecks and authoritative player identity for tools;
- idempotent asynchronous tool calls and late completion after session closure;
- controlled-session interruption, player preemption, end cleanup, stale callback rejection, and
  bounded autonomous group discussion, plus foreground/background/activity registry shutdown cleanup;
- old-save loading from legacy facts/events/relationship aggregates into provenance-aware memory;
- durable addon-confirmed-outcome idempotency across save/reload.

Run both loader checks with:

```sh
GRADLE_USER_HOME=/cache/gradle ./gradlew \
  :1.21.1-neoforge:test :1.21.1-neoforge:verifyApiJar \
  :1.20.1-forge:test :1.20.1-forge:verifyApiJar \
  --no-daemon --max-workers=1
```

## Release artifact verification

`scripts/verify-release-readiness.sh` is deliberately stricter than a normal developer build. It:

1. checks that both the POM and JAR for the exact configured Gemini Live Library coordinate exist in
   the public sshcrack Maven repository for both supported loaders;
2. points `GEMINI_LIVE_LIBRARY_DIR` at a nonexistent path so no composite build can substitute it;
3. uses `--refresh-dependencies` so a locally cached unpublished artifact cannot create a false pass;
4. clears stale collected JARs, enables release-version output, and runs `buildAndCollect` for both
   Stonecutter targets;
5. verifies both normal mod JARs and stripped addon API JARs, including the API/implementation
   separation;
6. verifies the runtime mod metadata constrains Gemini Live Library to the compatible semantic-version
   major (`[2.4.1,3.0.0)` for this release).

### Resolved 2026-09-24 (previously blocked 2026-09-07)

The configured dependency is `deps.gemini_live_lib_version=2.4.1`. `2.4.0` was never published (HTTP
`404` for both the POM and JAR, on both loader coordinates, confirmed again on 2026-09-24). `2.4.1`
**is** published: direct checks against the public sshcrack Maven repository returned HTTP `200` for
both the POM and the JAR of:

- `me.sshcrack:gemini_live_lib:2.4.1-1.21.1-neoforge`
- `me.sshcrack:gemini_live_lib:2.4.1-1.20.1-forge`

`GEMINI_LIVE_LIBRARY_DIR=/nonexistent GRADLE_USER_HOME=/home/hendrik/.gradle bash
scripts/verify-release-readiness.sh` now passes end to end (exit `0`) with no composite build present
(`settings.gradle.kts` printed `Warning: Gemini Live Library not found, skipping includeBuild`). It
produced and verified both normal mod JARs and both stripped addon-API JARs
(`build/libs/2.0.0-beta.2/mc_talking-2.0.0-beta.2-{forge+1.20.1,neoforge+1.21.1}.jar` and the matching
`mc_talking-api-2.0.0-beta.2-*` files), and confirmed each mod JAR's `mods.toml`/`neoforge.mods.toml`
constrains `gemini_live_lib` to `versionRange = "[2.4.1,3.0.0)"`.

While reproducing this, `scripts/verify-release-readiness.sh` itself had a latent bug: it read only
`mod.version` ("2.0.0") from `stonecutter.properties.toml` and never appended `mod.channel_tag`
("-beta.2"), so it searched for `mc_talking-2.0.0-*.jar` instead of the actually-collected
`mc_talking-2.0.0-beta.2-*.jar` and failed with "Missing collected artifact" even though the build
succeeded. The script now reads both properties and concatenates them, matching how
`build-logic` derives `fullVersion`.

Historical note: earlier iterations of this document referenced Gemini `2.3.6` (2026-09-07 audit) and
then `2.4.0` (2026-09-07 final review) as the target minimum; neither was ever published. `2.4.1` is
the current, verified, published target and is what `gradle.properties` and the loader metadata now
declare.

To rerun this verification after any future dependency bump:

```sh
GRADLE_USER_HOME=/cache/gradle bash scripts/verify-release-readiness.sh
```

## In-world validation record

The required client smoke test launches both real Minecraft clients, creates/enters a world, verifies
mod construction/mixins, stays in-world for the required tick window, and exits automatically. It is
necessary release evidence, but it does not exercise credentialed Gemini conversations by itself.

Before declaring roadmap item 12 complete, record an in-world pass for each behavior below against a
release-candidate build and the configured published Gemini library:

| Scenario | Required observation | Current status |
| --- | --- | --- |
| Player dialogue | player starts/stops a citizen conversation; audible tail drains; ownership releases | Manual credentialed validation required |
| Paired dialogue | two citizens converse; audio follows participants; final playback drains | Manual credentialed validation required |
| Controlled group turns | 3+ citizens take caller-selected turns with attributed shared history | Manual credentialed validation required |
| Interruption | barge-in/caller interruption stops the exact active turn and late audio is ignored | Manual credentialed validation required |
| Provider disconnect | bounded recovery or typed terminal failure; no leaked busy/provider slot | Manual credentialed validation required |
| Server shutdown | active/idle controlled sessions and provider/audio resources close cleanly | Manual credentialed validation required |

Automated fake-transport/runtime tests cover the state-machine semantics of these paths, but they are
not a substitute for the requested credentialed in-world release-candidate check. Record date,
Minecraft/loader, Talking Colonists build, Gemini Live Library build, and outcome when that check is
performed.

## Maintainer release notes draft

### Addon API generation 2

Talking Colonists now exposes a first-class addon integration surface under
`me.sshcrack.mc_talking.api`. Addons can compose prompt facts/instructions, register authorized AI
query/command tools, reserve citizen activities, observe and start conversations, confirm
provenance-aware memory outcomes, and orchestrate controlled multi-citizen sessions without reaching
into Talking Colonists manager, websocket, audio, mixin, or duck-interface internals.

The developer-only `mc_talking-api` artifact contains the supported API surface for IDE/compile use;
players still install only Talking Colonists. Public compile examples are verified against the stripped
API JAR for Forge 1.20.1 and NeoForge 1.21.1. API generation 2 is intentionally breaking: addons must
migrate old internal hooks rather than depend on compatibility shims.

Core now owns lifecycle/recovery details that addons previously had reason to work around, including
bounded provider recovery, ownership-safe busy/session cleanup, audible playback drain, stale audio
rejection, pregenerated playback interruption, model-specific voice fallback, and controlled session
cleanup. Addon-confirmed gameplay outcomes have durable provenance/idempotency, and legacy memory
saves migrate without dropping old facts/events/relationship aggregates.

Compatibility limits: no existing Colonist Errands release is declared compatible until a migrated
build is compiled and tested against API generation 2. Colony Meetings has an integration guide and
compile-checked example, but no external release was available to test. Gemini Live Library `2.4.1`
is now published for both supported loaders and `scripts/verify-release-readiness.sh` passes against
it; final release still remains blocked until the credentialed in-world matrix above is recorded by a
maintainer with a real Gemini API key.

## Draft response to addon maintainers

Talking Colonists now has a supported addon boundary rather than requiring mixins/reflection into its
conversation internals. For your migration, compile against the matching
`me.sshcrack:mc_talking-api` artifact and keep the normal Talking Colonists mod on the dev runtime.
Prompt additions use contributors, addon actions use the typed/authorized tool registry, gameplay
busy state uses activity reservations, durable gameplay outcomes use confirmed memory events, and
meeting/group orchestration uses controlled sessions. Provider clients, raw audio queues, slot maps,
and reconnect/watchdog bookkeeping intentionally remain core-owned.

The pinned Colonist Errands audit in `docs/addon-migration.md` maps each observed Talking Colonists
mixin/reflection/internal access to its supported replacement or explains why no raw replacement is
exposed. We are not claiming an existing Errands build is compatible yet: the next compatibility
milestone is a migrated addon build that compiles and runs against both declared loader targets.
