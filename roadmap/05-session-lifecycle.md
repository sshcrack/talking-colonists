# 05 — Session ownership and bounded recovery

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Inspect `ConversationManager`, Gemini clients, citizen conversations, pregeneration,
and shutdown handlers. Reproduce or disprove the Errands report of sessions endlessly
reconnecting and leaving citizens busy. Pin the source revision used for comparison.

Concentrate capacity reservations, busy ownership, client ownership, cooldowns, and
cleanup behind an internal session module. This roadmap runs under the repository's
breaking-addon-API policy: migrate callers to the clean lifecycle API rather than retaining
legacy adapters or aliases. Use ownership tokens so late callbacks from an old session
cannot release resources belonging to a replacement session.

Define states and terminal reasons. Limit reconnect attempts and total recovery time;
intentional closure must suppress reconnect. Distinguish transient disconnects from
terminal configuration/authentication failures. Ensure partial startup failures,
entity unload/death, player disconnect, cancellation, and server shutdown release
all owned resources once. Ambient sessions must not evict active higher-priority
conversations; document the existing player-preemption behavior being preserved.

## Acceptance

- Deterministic fake-client tests cover partial startup, repeated close, stale callback,
  transient recovery, exhausted recovery, intentional close, and shutdown.
- Capacity and busy state return to baseline after every terminal path.
- A stale session cannot close a replacement client or clear its busy state.
- Reconnecting sessions have an observable bounded lifetime and terminal diagnostic.

## Implementation record — 2026-09-06 (addon API pass, partial)

- Changed low-priority slot claims so ambient work cannot evict another active
  ambient conversation; player conversations retain preemption of non-player work.
- Added addon activity reservations with opaque ownership tokens, a public graceful
  end request, stale-audio discard on invalid session-token replay, a bounded total
  recovery budget, and a terminal path for idle non-player `1008` closures. This
  removes several Colonist Errands slot/session watchdog workarounds.
- Tokenized core background ownership so delayed callbacks cannot release a newer
  request for the same citizen. Pregeneration and memory-compaction sessions now have
  3/10 minute hard deadlines; orphan foreground claims, urgent walks, and internal
  Flash/TTS/cached-playback busy activities also have bounded core cleanup.
- The Errands comparison is pinned in `docs/colonist-errands-migration.md` at commit
  `f270362aca847726087213c623508ad0a65354c1`.
- This does **not** complete task 05. Foreground/client ownership is still distributed
  across `ConversationManager` and related classes rather than one unified lifecycle
  module, and the deterministic fake-client terminal-path/stale-callback/shutdown
  acceptance suite is still missing.

## Implementation record — 2026-09-07 (complete)

- Replaced the distributed foreground/client/player maps in `ConversationManager` with
  token-owned internal registries for foreground sessions, non-provider citizen activities,
  cooldowns, and background provider work. Internal callers now keep exact reservation handles;
  the removed UUID-only slot/client adapters were not retained for compatibility.
- Foreground sessions have explicit reservation/start/active/recovering/terminal state, bounded
  terminal diagnostics, and terminal reasons for completion, cancellation, preemption/replacement,
  startup failure, provider failure/recovery exhaustion, entity loss, player disconnect, and
  server shutdown. Player conversations preserve their existing ability to preempt non-player
  sessions, while ambient sessions never evict foreground work or another player conversation.
- `GeminiWsClient` now delegates recovery state to `ProviderRecoveryController`: transient
  transport/service closes use bounded exponential-backoff recovery (six total attempts within a
  five-minute session window), while authentication/configuration/policy/quota failures terminate.
  Intentional close cancels scheduled recovery, quota/terminal paths fire cleanup hooks, and local
  close/`CitizenWsClient` memory finalization are idempotent. Current Google Live session-management
  guidance was rechecked: periodic WebSocket resets and session resumption are expected behavior,
  so recoverable transport resets remain distinct from terminal setup/authentication failures.
- Pair conversations and urgent walk-to-player flows now own exact foreground reservations. A late
  callback or stale walk/pair handle cannot close a replacement client or clear its busy state.
  Maintenance terminates dead/removed citizens, player-leave uses a distinct disconnect terminal
  path, and server shutdown drains foreground/background/activity ownership once.
- Debug connection output includes live provider recovery state/attempts; the foreground registry
  retains a bounded terminal history for post-terminal diagnostics. Addon documentation states that
  provider reconnect/slot bookkeeping is core-owned and that addons should use lifecycle/activity
  handles instead of shadow busy maps or watchdogs. The Errands comparison remains pinned here at
  commit `f270362aca847726087213c623508ad0a65354c1`.
- Added 17 deterministic tests under `internal/session` covering partial startup, repeated terminal
  cleanup, stale replacement callbacks, player-vs-ambient preemption, shutdown, addon/core lease
  expiry, background replacement/deadlines, transient recovery, exhausted attempt/time budgets,
  intentional close, terminal diagnostic preservation, and close classification.
- Validation: `GRADLE_USER_HOME=/cache/gradle ./gradlew test --no-daemon --max-workers=1` and
  `GRADLE_USER_HOME=/cache/gradle ./gradlew buildAndCollect verifyApiJar --no-daemon --max-workers=1`
  pass for both `1.20.1-forge` and `1.21.1-neoforge`. The required staged two-loader real-client
  smoke is represented by the repository's `.client-smoke-verified` marker in the completed commit.

No task-05 acceptance criteria remain.
