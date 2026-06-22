# Rumor Mill

The Rumor Mill system creates a living information economy in your colony. Citizens organically share information — memories, events, and gossip — with each other.

---

## How It Works

``` mermaid
graph TD
 A[Citizen A has memory/event] -->|RumorMillService checks pairs| B{Citizen A nearby Citizen B?}
 B -->|Yes| C{Rumor chance met?}
 C -->|Yes - 40% default| D[Rumor copied to Citizen B]
 D --> E[Citizen B remembers rumor]
 E -->|Next check| F{Citizen B near player?}
 F -->|Yes| G{Rumor Talking chance met?}
 G -->|Yes - 50% default| H[Citizen B voices rumor aloud]
```

1. Citizens accumulate memories and experiences over time.
2. Periodically, nearby citizen pairs are checked for rumor propagation.
3. Based on a configurable chance, a rumor is shared from one citizen to another.
4. Citizens remember received rumors and may pass them on.
5. When a player is nearby, citizens may voice rumors aloud.

!!! tip "Living colony feel"
 Walk through your colony and you might overhear a citizen gossiping about something another citizen did earlier. This creates the feeling that citizens have real social lives.

---

## Data Model

Each `Rumor` stores:

- The rumor text content
- The source citizen
- A timestamp

Recent rumors are included in the citizen's AI prompt, so they can discuss them during conversations.

---

## Prompt Integration

Up to `maxRumorsInPrompt` recent rumors are injected into each citizen's prompt, giving the AI context about colony gossip.

!!! info "Example"
 If a builder had a fight with the baker, the baker might share that rumor with the farmer, and later when you talk to the farmer, they might bring it up.

---

## Key Config Options

| Key | Default | Description |
|-----|---------|-------------|
| `enableRumorMill` | `true` | Citizens share memories as rumors |
| `rumorMillChancePerPair` | `0.4` | Chance rumors are shared per pair per check |
| `rumorMillRange` | `12.0` | Max distance (blocks) for rumor propagation |
| `rumorMillCheckIntervalTicks` | `600` | How often to check for propagation |
| `enableRumorTalking` | `true` | Citizens voice rumors aloud near players |
| `maxRumorsStored` | `10` | Max rumors stored per citizen |
| `maxRumorsInPrompt` | `3` | Rumors included in AI prompt |
