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
