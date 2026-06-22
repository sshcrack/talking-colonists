# Broadcast System

The Broadcast System allows players to ask citizens to spread messages colony-wide through word-of-mouth propagation.

!!! warning "Player-only feature"
 Broadcasts can only be initiated during **player-initiated conversations** - citizens won't spontaneously broadcast during autonomous conversations.

---

## How It Works

``` mermaid
graph TD
 A[Player requests broadcast] -->|AI invokes initiate_broadcast tool| B[ColonyBroadcast created]
 B --> C[BroadcastPropagationService]
 C -->|Periodic check| D{Another citizen in range?}
 D -->|Yes| E[Citizen receives broadcast]
 E -->|Can propagate further| C
 E -->|Player nearby?| F[Citizen yells aloud]
```

1. **Initiation** - During a conversation, the player asks a citizen to spread a message.
2. **AI Tool** - The AI invokes the `initiate_broadcast` tool (player-only function).
3. **Propagation** - The `BroadcastPropagationService` periodically checks for active broadcasts and propagates them to nearby citizens within range.
4. **Chain effect** - Each citizen that receives a broadcast can pass it on to others, creating a wave across the colony.
5. **Yelling** - Citizens can announce broadcasts aloud when a player is nearby (`enableBroadcastYelling`).

!!! tip "Use cases"
 - "Tell everyone to meet at the town hall!"
 - "Spread the word: we need more wood for the expansion."
 - "Announce that a raid is coming!"

---

## Data Model

Each `ColonyBroadcast` stores:

- The message text
- The source citizen who started it
- A timestamp

---

## Prompt Integration

Recent broadcasts are included in citizen prompts so they can discuss them during conversations.

---

## Key Config Options

| Key | Default | Description |
|-----|---------|-------------|
| `enableBroadcastPropagation` | `true` | Enable broadcast propagation |
| `broadcastPropagationIntervalTicks` | `300` | How often to check for propagation |
| `broadcastMaxPropagationsPerTick` | `5` | Max propagations per tick |
| `broadcastPropagationRange` | `24.0` | Max distance between citizens |
| `maxBroadcastsInPrompt` | `3` | Broadcasts included in AI prompt |
| `enableBroadcastYelling` | `true` | Citizens announce broadcasts aloud |
| `broadcastYellingRange` | `24.0` | Max distance to hear broadcast |
