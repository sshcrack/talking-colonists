# Conversations

The mod supports two types of conversations: **player** ↔ **citizen** and **citizen** ↔ **citizen**.

---

## Player ↔ Citizen

| Step | Detail |
|------|--------|
| **Initiation** | Left-click a citizen while holding the **Citizen Communication Device** |
| **Technology** | Uses the Gemini Live API for real-time, bidirectional voice streaming |
| **Response context** | Citizens respond based on their personality, memories, colony status, recent events, rumors, and broadcasts |
| **Lifecycle** | Managed by `ConversationManager`, which tracks active sessions, cooldowns, and slot allocation |
| **Priority system** | Player conversations are high-priority and can evict lower-priority sessions (mumbling, citizen-to-citizen) when the connection limit is reached |

!!! tip "Hint"
 You can have up to 3 simultaneous conversations on the Gemini free tier with Flash 3.

---

## Citizen ↔ Citizen

Citizens can autonomously start conversations with each other when nearby. This creates a living colony where citizens discuss colony events, share rumors, gossip, and build relationships.

!!! example "Screenshot needed"
 Two colonists standing close together with `(In conversation)` status indicators visible in their name tags.

### Modes

Controlled by `conversationMode`:

| Mode | How it works | Quality |
|------|-------------|---------|
| `LIVE_WEBSOCKETS` | Two Gemini Live WebSocket sessions cross-feed audio to each other in real time | Cheaper/faster |
| `FLASH_TTS` | Flash generates a script, then Gemini TTS renders multi-speaker audio | Higher quality, ~10/day limit |
| `AUTO` | (Default) Tries Flash + TTS first; falls back to Live WebSockets if the pipeline fails | Best of both |

---

## Random Conversations

Citizens may randomly start conversations with nearby citizens based on a configurable chance and interval.

### Key Config Options

| Key | Default | Description |
|-----|---------|-------------|
| `enableCitizenToCitizenConversation` | `true` | Enable autonomous citizen conversations |
| `conversationMode` | `AUTO` | Mode for citizen-to-citizen conversations |
| `enableRandomConversations` | `true` | Citizens randomly start conversations |
| `randomConversationChance` | `0.05` | :100: Chance per check interval |
| `respondInGroups` | `false` | Citizens respond in group chat |
