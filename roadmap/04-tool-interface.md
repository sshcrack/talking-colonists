# 04 — Public tools and authorized execution

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Expose a supported addon tool registry under `api`, replacing the need to access
`AITools` maps. Preserve built-in tool behavior and existing function names/config
entries. Define namespaced addon IDs and a deterministic collision-free mapping to
Gemini-valid function names, checking provider naming constraints.

Give execution an explicit context: session identity, acting citizen, colony, and
authenticated initiating player when present. Central dispatch must validate schema,
current enabled status, session scope, and permission at execution time. Resolve
colony permissions from authoritative server state; the model cannot supply its actor
or rank. NPC-only sessions receive no implicit player authority. Marshal world
mutations to the server thread without deadlocking callback threads.

Support synchronous queries and asynchronous commands with operation IDs and
structured `accepted`, `completed`, `failed`, and cancelled outcomes. Route eventual
results to the owning session when available, and provide an addon completion hook
when it has ended. Define duplicate invocation handling so retries cannot repeat a
world-changing operation. Use bounded result retention, not an unbounded global map.

## Acceptance

- A sample addon registers a query and delayed command without mixins/reflection.
- Tests reject disabled, malformed, unauthorized, wrong-session, and actor-spoofed
  calls, including rank changes between advertisement and execution.
- Repeated call IDs do not duplicate side effects; distinct operations remain usable.
- Delayed completion after session closure safely cleans up and does not reopen it.
- Document execution/thread/error contracts and migration from `FunctionAction`.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Added public `AiToolRegistry`, `AiTool`, `AiToolScope`, and authoritative
  `AiToolContext`. Addon IDs are namespaced and mapped deterministically to provider
  function names; built-in tools/config continue to work and the list-tools command
  understands addon registrations.
- Central dispatch resolves the initiating `ServerPlayer` from the active session,
  rechecks enabled/scope/custom authorization at call time, and never accepts actor
  identity from model parameters. Colonist Errands no longer needs reflective access
  to `AITools` merely to register tools or recover the player context.
- Added a compile-checked authorization example and registry tests.
- This does **not** complete task 04. Schema validation, a core permission abstraction,
  managed server-thread world mutation, asynchronous operation IDs/results,
  cancellation, duplicate-call idempotency, bounded result retention, and the full
  authorization/spoofing test matrix remain.

## Implementation record — 2026-09-07 (completion)

- Split addon tools into the explicit `AiQueryTool` and `AiCommandTool` contracts.
  Queries are synchronous reads; commands return a `CompletionStage` and receive a
  core-generated operation ID. The public `AiToolContext` now exposes authoritative
  session identity, citizen, colony, and authenticated initiating player.
- Added central `AiToolDispatcher`/schema validation. Core rechecks enabled state,
  session scope, stable `AiToolPermission`, and custom authorization on the Minecraft
  server thread immediately before execution. Parameter JSON is validated against the
  declared object schema, including required/type/enum/unknown-field checks, so model
  arguments cannot smuggle actor, rank, or session authority.
- Commands use the provider function-call ID as a per-session idempotency key. Exact
  retries reuse the existing operation without repeating side effects; conflicting
  reuse is rejected. Active operations and retained terminal outcomes are bounded,
  cancellation has a structured outcome, and intentional session close drops retained
  state.
- Delayed command completion is routed only to the still-active owning Gemini session.
  Core never reconnects a closed/unavailable session to deliver a result. The addon
  `onCompletion` hook still receives the terminal outcome and whether delivery occurred.
  Later asynchronous world mutations use `runOnServerThread`/`supplyOnServerThread`.
- Gemini call-ID extraction preserves ordering across mixed batched built-in and addon
  calls, preventing a preceding built-in tool from shifting an addon's idempotency key.
- Updated the compile-checked addon example plus `docs/addon-api.md` and
  `docs/addon-migration.md` with query/command, threading, authorization, outcome,
  idempotency, and `FunctionAction` migration contracts. No legacy addon compatibility
  shim was retained; the repository's intentional breaking API policy applies.
- Validation passed on both supported loaders: full JUnit suites, `verifyApiJar`, and
  `./gradlew buildAndCollect --no-daemon`. The required real-client smoke test passed
  `1.21.1-neoforge` and `1.20.1-forge`; because the sandbox could not reach Mojang's
  asset host, it used the documented `CLIENT_SMOKE_METADATA_ONLY_ASSETS=1` fallback.
