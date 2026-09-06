# 05 — Session ownership and bounded recovery

## Agent prompt

Implement this task using the shared instructions in [README.md](README.md).

Inspect `ConversationManager`, Gemini clients, citizen conversations, pregeneration,
and shutdown handlers. Reproduce or disprove the Errands report of sessions endlessly
reconnecting and leaving citizens busy. Pin the source revision used for comparison.

Concentrate capacity reservations, busy ownership, client ownership, cooldowns, and
cleanup behind an internal session module. Preserve existing public callers through
compatibility adapters where necessary. Use ownership tokens so late callbacks from
an old session cannot release resources belonging to a replacement session.

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
