# General Settings

Core configuration options for the mod's behavior.

---

## General

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `language` | String | `"en-US"` | Language code for speech recognition and synthesis. See [supported languages](https://ai.google.dev/gemini-api/docs/live-api/capabilities#supported-languages) for the full list. |
| `sendErrorsToPlayers` | Boolean | `true` | If true, errors are sent to OP players in chat. If false, errors are only logged to console. |
| `disabledTools` | String List | `[]` | List of AI tool names to disable (e.g. `["leave_colony", "drop_item"]`). |

!!! tip "Disabling tools"
 Adding tools to `disabledTools` prevents the AI from invoking them. Common practice is to disable `leave_colony` and `drop_item` for safety.

---

## Interaction

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `respondInGroups` | Boolean | `false` | Whether citizens respond if the player is in a group. |
| `sendMumblingAndConversationsToChat` | Boolean | `false` | If true, text from mumbling and citizen-to-citizen conversations is sent to nearby players in chat. |
| `continueWorkDuringConversation` | Boolean | `false` | If true, citizens continue wandering during conversations. If false, they stay in place. |
| `maxConversationDistance` | Double | `8.0` | Maximum distance (blocks) before a conversation is ended. Range: 1.0–100.0. |
| `modality` | Enum | `AUDIO` | Response format: `TEXT`, `AUDIO`, or `TEXT_AND_AUDIO`. |

---

## Resource Management

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `maxConcurrentAgents` | Integer | `3` | Max simultaneous AI agents. Flash 3 limited to 3 on free tier, Flash 2.5 to 1. Range: 1–100. |
| `maxConcurrentBackground` | Integer | `3` | Max concurrent background connections for pregeneration and compaction. Range: 1–10. |

---

## Modality Modes

| Value | Description | Use case |
|-------|-------------|----------|
| `TEXT` | AI responds with text only (no audio streaming) | Accessibility, debug |
| `AUDIO` | AI responds with audio only (default) | Normal voice chat |
| `TEXT_AND_AUDIO` | + AI responds with both text and audio | Subtitles + voice |

!!! tip "Text + Audio mode"
 Enabling `TEXT_AND_AUDIO` is great for accessibility — you can read what the citizen said if the audio is unclear.
