# Urgent-contact lifecycle

Urgent contact is owned by `UrgentContactLifecycleModule`. The module keeps the immutable journey
origin (`originPlayerId`) separate from the foreground registry's current direct participant.
Minecraft entity lookup, navigation, need inspection, and announcement startup live behind
`MinecraftUrgentContactAdapter` so lifecycle transitions are deterministic in unit tests.

## Ownership and phases

A contact owns one `ForegroundReservation` for its entire ambient lifetime:

1. `WALKING` reserves an ambient `URGENT_CONTACT` slot, marks only `URGENT_WALKING`, and navigates
   toward the origin player.
2. Arrival clears the walking fact and starts the one-sided announcement on the **same reservation**.
   There is no release/re-reserve gap, so failed announcement startup has an exact owner to release.
3. `ANNOUNCING` remains ambient (`playerId == null`). Provider readiness therefore never derives
   `LISTENING`; only direct-player participation may advertise microphone availability.
4. Audible completion ends the owned reservation with `COMPLETED`. Provider failure, need
   resolution, invalid context, departure, timeout, and explicit cancellation have explicit terminal
   outcomes and release only that contact's token.

Terminal removal happens before navigation/status/provider cleanup. Every provider callback carries
an immutable contact id, so duplicate close callbacks and callbacks from replaced contacts are
ignored. Cancellation and completion are therefore at-most-once from the lifecycle's perspective.

## Player takeover

A successfully started normal direct conversation calls `onPlayerTakeover`:

- During walking, `ConversationManager` has already replaced the ambient reservation with a normal
  player reservation. The urgent lifecycle stops navigation and records `TAKEN_OVER`, but does not
  end the replacement.
- During announcement, the existing `CitizenWsClient` may be promoted in-place. The foreground token
  is intentionally retained; the urgent lifecycle relinquishes responsibility without ending it.
- If announcement completion/cancellation wins the race first, it ends only the old urgent token and
  the player conversation starts normally on a new token.
- Once takeover wins, resolving the original urgent need has no lifecycle owner left and therefore
  cannot end the direct player conversation. The participation/microphone modules continue to own
  listening and input routing for that direct session.

## Cancellation and validity

The lifecycle terminates the owned urgent contact when the origin player departs, the citizen becomes
invalid, the citizen/player target context is no longer same-world valid, the urgent need disappears,
the walk times out, foreground ownership is externally lost, or the server stops. Cleanup stops
navigation, clears the walking participation fact, and ends exactly the owned reservation when that
reservation still belongs to the urgent contact.

Server shutdown invokes urgent-contact shutdown before `ConversationManager.cleanup()` so the module
gets first opportunity to release its own reservations deliberately.

## Cooldowns

The automatic per-player urgent-contact cooldown preserves the previous behavior, but uses the
module's monotonic clock:

- A successfully accepted walk consumes the cooldown immediately.
- Therefore a later arrival/announcement-start failure does not create an automatic retry loop.
- With walk-to-player disabled, an immediate announcement-start failure is not a successful start and
  does not consume the player cooldown.
- Player departure clears that player's urgent-contact cooldown, matching the prior handler behavior.

The ordinary per-citizen automatic-conversation cooldown remains owned by `ConversationManager` and
is still recorded from active ambient foreground terminal events.
