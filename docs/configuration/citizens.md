# Citizens Configuration

All settings related to citizen behavior, conversations, memory, and AI interactions.

---

## General

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableColonyStatsMentions` | Boolean | `true` | Citizens occasionally mention colony milestones in idle mumbles and conversations. |
| `citizenInteractionRange` | Double | `10.0` | Range (blocks) within which a citizen can be triggered to mumble or start a conversation. Range: 1.0–100.0. |
| `citizenCooldownSeconds` | Integer | `120` | Minimum seconds after a citizen's auto-session ends before they can be selected for another. 0 = disable. Range: 0–10000. |

---

<details markdown="1">
<summary> **Citizen-to-Citizen Conversations**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableCitizenToCitizenConversation` | Boolean | `true` | Citizens can start conversations with each other without player involvement. |
| `enableConversationSummaryAndMemorize` | Boolean | `false` | AI summarizes notable events from citizen conversations for memory. |
| `conversationMode` | Enum | `AUTO` | How citizen-to-citizen conversations work. |

### Conversation Modes

| Mode | Description |
|------|-------------|
| `LIVE_WEBSOCKETS` | Two Gemini Live WebSocket sessions cross-feed audio in real time. Cheaper/faster. |
| `FLASH_TTS` | Flash generates a script, then Gemini TTS renders multi-speaker audio. Higher quality, ~10/day limit. |
| `AUTO` | (Default) Tries Flash + TTS first, falls back to Live WebSockets if quota is exhausted. |
</details>

---

<details markdown="1">
<summary> **Random Conversations**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableRandomConversations` | Boolean | `true` | Citizens randomly start conversations based on the chance below. |
| `randomConversationChance` | Double | `0.05` | :100: Chance a pair starts a random conversation per check interval. Range: 0.0–1.0 (step 0.01). |
| `randomConversationCheckIntervalTicks` | Integer | `400` | How often (ticks) to check for random conversations. 20 ticks = 1 second. Range: 1–10000. |
</details>

---

<details markdown="1">
<summary> **Mumbling**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `mumblingChance` | Double | `0.05` | Chance a nearby citizen starts mumbling per check interval. Range: 0.0–1.0 (step 0.01). |
| `mumblingCheckIntervalTicks` | Integer | `200` | How often (ticks) to check for mumbling citizens. Range: 1–10000. |
</details>

---

<details markdown="1">
<summary> **Citizen-Initiated Contact**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableCitizenInitiatedContact` | Boolean | `true` | Citizens with urgent needs proactively speak to nearby players. |
| `citizenContactBaseChance` | Double | `0.5` | Base chance per check interval an urgent citizen speaks (multiplied by urgency weight). Range: 0.0–1.0 (step 0.01). |
| `citizenContactCheckIntervalTicks` | Integer | `80` | How often (ticks) to check for citizens to initiate contact. Range: 1–10000. |
| `enableUrgentContactWalkToPlayer` | Boolean | `true` | Urgent citizens walk to and follow the player, auto-starting conversation on proximity. |
| `urgentContactSearchRange` | Double | `30.0` | Search radius (blocks) for urgent contacts. Range: 5.0–100.0. |
| `blockingTaskUrgencyMultiplier` | Double | `3.0` | Extra urgency weight when citizen is stuck (missing tools/items). Range: 0.0–10.0 (step 0.1). |
| `playerUrgentContactCooldownSeconds` | Integer | `60` | Minimum cooldown (seconds) between urgent contacts for the same player. 0 = disable. Range: 0–10000. |
| `citizenCasualGreetingWeight` | Double | `0.1` | Base weight for casual greetings (multiplied by `citizenContactBaseChance`). Range: 0.0–1.0 (step 0.01). |
</details>

---

<details markdown="1">
<summary> **Voice Chat**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `citizenVoiceWhisper` | Boolean | `true` | Citizens use whisper volume when talking. |
| `citizenVoiceDistance` | Integer | `0` | Maximum voice distance for citizen audio. 0 = use default distance. |
</details>

---

<details markdown="1">
<summary> **Voice Pregeneration**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enablePregeneration` | Boolean | `true` | Pregenerate audio for greetings and threats to reduce latency. |
| `pregeneratedGreetingDistance` | Double | `6.0` | Distance (blocks) within which passing citizens trigger a pregenerated greeting. Range: 1.0–20.0. |
| `threatPlayCooldownMs` | Integer | `15000` | Cooldown (ms) between threat pregeneration plays. 0 = disable. Range: 0–60000. |
| `maxPregeneratedGreetingsPerCitizen` | Integer | `5` | Maximum pregenerated greetings stored per citizen. Range: 0–100. |
| `maxGreetingsPerTickInterval` | Integer | `1` | Maximum pregenerated greetings that may play in a single tick interval. Range: 1–10. |
| `enablePlayerGreetingPregen` | Boolean | `true` | Pregenerate player-specific greetings for frequent citizen-player pairs. |
| `playerGreetingDistance` | Double | `8.0` | Distance (blocks) within which citizen triggers a pregenerated player greeting. Range: 1.0–20.0. |
</details>

---

<details markdown="1">
<summary> **Raid Trauma**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `raidTraumaDurationSeconds` | Integer | `1200` | How long (seconds) citizens express post-raid trauma in prompts. 0 = disable. Range: 0–7200. |
</details>

---

<details markdown="1">
<summary> **Colony Events**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `colonyEventWindowSeconds` | Integer | `1200` | How long (seconds) colony lifecycle events appear in citizen prompts. 0 = disable. Range: 0–7200. |
</details>

---

<details markdown="1">
<summary> **Rumor Mill**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableRumorMill` | Boolean | `true` | Citizens share memories as rumors with each other. |
| `rumorMillCheckIntervalTicks` | Integer | `600` | How often (ticks) to check for rumor propagation. Range: 1–72000. |
| `rumorMillRange` | Double | `12.0` | Maximum distance (blocks) for rumor propagation between citizens. Range: 1.0–100.0. |
| `rumorMillChancePerPair` | Double | `0.4` | Chance rumors are shared between a citizen pair per check. Range: 0.0–1.0 (step 0.05). |
| `rumorMillMaxPropagationsPerTick` | Integer | `3` | Maximum rumor propagations per tick. Range: 1–100. |
| `enableRumorTalking` | Boolean | `true` | Citizens voice rumors aloud when a player is nearby. |
| `rumorTalkingChance` | Double | `0.5` | Chance a rumor is voiced aloud when a player is nearby. Range: 0.0–1.0 (step 0.05). |
| `rumorTalkingRange` | Double | `12.0` | Maximum distance (blocks) for a player to witness voiced rumors. Range: 1.0–50.0. |
| `maxRumorsStored` | Integer | `10` | Maximum rumors stored per citizen. Range: 1–100. |
| `maxRumorsInPrompt` | Integer | `3` | How many rumors to include in a citizen's AI prompt. 0 = disable. Range: 0–20. |
</details>

---

<details markdown="1">
<summary> **Colony Broadcast**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableBroadcastPropagation` | Boolean | `true` | Players can ask citizens to broadcast messages across the colony. |
| `broadcastPropagationIntervalTicks` | Integer | `300` | How often (ticks) to check for broadcast propagation. Range: 1–72000. |
| `broadcastMaxPropagationsPerTick` | Integer | `5` | Maximum broadcast propagations per tick. Range: 1–100. |
| `broadcastPropagationRange` | Double | `24.0` | Maximum distance (blocks) between citizens for broadcast propagation. Range: 1.0–1000.0. |
| `maxBroadcastsInPrompt` | Integer | `3` | How many recent broadcasts to include in a citizen's prompt. 0 = disable. Range: 0–20. |
| `maxBroadcastsStored` | Integer | `20` | Maximum broadcasts stored per citizen. Range: 1–100. |
| `enableBroadcastYelling` | Boolean | `true` | Citizens announce broadcasts aloud when a player is nearby. |
| `broadcastYellingRange` | Double | `24.0` | Maximum distance (blocks) for a player to hear a broadcast aloud. Range: 1.0–10000.0. |
</details>

---

<details markdown="1">
<summary> **Personality**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enablePersonalityArchetypes` | Boolean | `true` | Randomly assign a personality archetype to each citizen. |
| `customPersonalityArchetypes` | String List | `[]` | Custom personality archetype strings added to the random pool. |
</details>

---

<details markdown="1">
<summary> **Colony Diplomacy**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `enableColonyDiplomacy` | Boolean | `true` | Citizens reference neighboring colonies and diplomatic standing (allies, enemies) in conversations. |
</details>

---

<details markdown="1">
<summary> **Memory**</summary>

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `memoryMode` | Enum | `LIVE` | How memory compaction is performed. |
| `enableMemoryCompaction` | Boolean | `true` | Periodically compact and summarize citizen memories. |
| `memoryCompactionIntervalTicks` | Integer | `100` | How often (ticks) to check for memory compaction candidates. Range: 20–72000. |
| `memoryCompactionThreshold` | Integer | `15` | :100: Maximum events/facts before compaction is triggered. Range: 1–500. |

### Memory Modes

| Mode | Description |
|------|-------------|
| `LIVE` | Uses a text-only WebSocket session for memory compaction (default). |
| `FLASH` | Uses the Gemini Flash API for memory compaction. |
</details>
