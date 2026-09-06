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
