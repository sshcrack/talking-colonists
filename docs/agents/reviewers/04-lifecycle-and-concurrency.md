# Lifecycle and concurrency reviewer

Read `docs/agents/reviewers/COMMON.md` and follow it with fixed point
`{{FIXED_POINT}}`.

Review ownership and state transitions across `ConversationManager`,
`conversations/`, `internal/session/`, handlers, background work, and the runtime side
of controlled/autonomous conversations.

## Invariants to stress

- Every foreground/background reservation has one owner and exactly one terminal
  release path, including setup failures, provider errors, cancellation, disconnect,
  player/citizen removal, and server shutdown.
- Busy/cooldown/activity state reflects actual ownership; failed starts do not leave
  ghost reservations and late callbacks cannot clear a newer session's state.
- Close/stop/cancel operations are idempotent and safe when callbacks race with them.
- Futures, executors, websocket callbacks, Minecraft server-thread work, and voice
  callbacks cross thread boundaries deliberately. Shared mutable state has an actual
  synchronization/serialization strategy rather than relying on timing.
- Recovery/reconnect loops are bounded, cancel with the owning session, and cannot
  revive a session that has already terminated or been replaced.
- Controlled turns and autonomous discussions cannot overlap illegally, steal another
  session's citizen/activity reservation, or deliver late results into a new turn.
- Exception paths transition the externally visible handle/state to a terminal or
  recoverable state that matches the public contract.
- Resource cleanup order does not deadlock while waiting for audio drain, callbacks,
  or executor tasks that themselves need the same state/lock/thread.

Trace state changes in both directions: who sets a state/reservation and every path
that clears/replaces it. Look for ABA-style bugs where an old asynchronous completion
mutates state belonging to a newer session.
