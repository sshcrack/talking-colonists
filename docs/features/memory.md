# Memory System

Citizens remember past events, conversations, and relationships, creating a persistent history that shapes their future behavior.

!!! info "It's not just chat history"
 Citizens don't just remember what you said - they remember events like raids, job changes, new buildings, and their relationships with other citizens.

---

## What Citizens Remember

| Memory type | Details |
|-------------|---------|
| **Conversations** | Summaries of past conversations with players |
| **Colony events** | Raids, new buildings, births, deaths, job changes |
| **Relationships** | Inter-citizen relationship data tracked through `CitizenRelationshipMemory` |
| **Player interactions** | Positive and negative interactions tracked via relationship change records |
| **Custom events** | The AI can use the `add_event_to_memory` tool to record important events |

---

## Memory Modes

| Mode | Description |
|------|-------------|
| `LIVE` | (Default) Uses a text-only WebSocket session for memory compaction |
| `FLASH` | Uses the Gemini Flash API for memory compaction |

---

## Memory Compaction

Over time, citizen memories accumulate. To prevent the prompt from growing too large, the mod periodically compacts old memories:

1. When memory events/facts exceed the `memoryCompactionThreshold` (default: 15), compaction is triggered.
2. Old memories are summarized and compressed into concise summaries.
3. Compaction runs in the background using the configured `memoryCompactionIntervalTicks`.

!!! tip "Why compaction matters"
 Without compaction, a citizen who's been around for weeks would have a massive prompt history, slowing down responses and costing more API credits.

---

## Data Models

- **`CitizenMemories`** - Stores memories per citizen (events, facts, summaries).
- **`CitizenRelationshipMemory`** - Tracks inter-citizen relationship data.
- **`CitizenRelationshipChangeType`** - Defines how relationships can evolve.

---

## Key Config Options

| Key | Default | Description |
|-----|---------|-------------|
| `memoryMode` | `LIVE` | Memory compaction method |
| `enableMemoryCompaction` | `true` | Periodic memory compaction |
| `memoryCompactionIntervalTicks` | `100` | How often to check for compaction |
| `memoryCompactionThreshold` | `15` | :100: Max events before compaction triggers |
