# Mumbling

The mumbling system adds ambient life to your colony by having citizens occasionally mutter to themselves when idle.

!!! tip "Atmosphere"
 Walk through your colony and hear citizens muttering about their day - it makes the colony feel alive even when you're not actively talking to anyone.

---

## How It Works

1. **Periodic check** - The `CitizenMumblingHandler` runs on a configurable interval (`mumblingCheckIntervalTicks`).
2. **Probability** - Each idle citizen has a chance to start mumbling (`mumblingChance`).
3. **Content** - Citizens mutter about their current tasks, needs, or random thoughts.
4. **Audio** - Mumbling is rendered through the voice chat system, using the configured voice distance and whisper settings.
5. **Manual trigger** - The Mumbling Trigger Device item can force a citizen to start mumbling.

!!! example "Screenshot needed"
 A colonist with mumbling text visible in chat (when `sendMumblingAndConversationsToChat` is enabled), showing what they're muttering about.

---

## Key Config Options

| Key | Default | Description |
|-----|---------|-------------|
| `mumblingChance` | `0.05` | Chance a citizen starts mumbling per check |
| `mumblingCheckIntervalTicks` | `200` | How often to check for mumbling (20 ticks = 1s) |
