# AI Tools

AI tools are function declarations the Gemini AI can invoke during a conversation to interact with the game world or gather information.

!!! info "How AI tools work"
 When a citizen is talking to you, the AI decides when to call these tools. For example, if you ask "what's around here?", the AI calls `describe_surroundings`. You don't need to do anything — the AI handles it automatically.

---

## Tool Categories

| Category | Scope | Tools |
|----------|-------|-------|
| **General** | Available in all conversation contexts (player, mumbling, citizen-to-citizen) | 11 tools |
| **Player-Only** | Only callable when a player is directly speaking | 4 tools |

---

## General Tools

<details markdown="1">
<summary> <code>get_citizen_info</code> — Get citizen details</summary>

**Class:** `GetCitizenInfoAction`

Gets detailed info about a specific citizen (skills, job, family, saturation, quests, etc.).

**Parameters:** `citizen_name` (string, required)

*Used when you ask "how's the builder doing?" or "tell me about yourself."*
</details>

<details markdown="1">
<summary> <code>list_citizens</code> — List all citizens</summary>

**Class:** `ListCitizenAction`

Lists all citizens in the colony by name. No parameters.

*Used when you ask "who lives here?" or "how many citizens do we have?"*
</details>

<details markdown="1">
<summary> <code>get_inventory</code> — Citizen inventory</summary>

**Class:** `GetInventoryAction`

Lists current items in the citizen's inventory. No parameters.

*Used when you ask "what are you carrying?"*
</details>

<details markdown="1">
<summary> <code>get_colony</code> — Colony info</summary>

**Class:** `GetColonyAction`

Gets colony info (name, buildings, happiness, research, statistics, events).

**Parameters:** `colony_id` (integer, optional)

*Used when you ask "how's the colony doing?"*
</details>

<details markdown="1">
<summary> <code>describe_surroundings</code> — Immediate surroundings</summary>

**Class:** `DescribeSurroundingsAction`

Describes immediate surroundings (~20 blocks): time, weather, nearby entities, colony buildings. No parameters.

*Used when you ask "what's around here?"*
</details>

<details markdown="1">
<summary> <code>describe_building</code> — Colony building</summary>

**Class:** `DescribeBuildingAction`

Describes a colony building (type, level, workers, type-specific info).

**Parameters:** `type` (enum of building types). `"own_building"` for the citizen's workplace.

*Used when you ask "what's in the warehouse?" or "tell me about your workplace."*
</details>

<details markdown="1">
<summary> <code>end_conversation</code> — End conversation</summary>

**Class:** `EndConversationAction`

Terminates the current autonomous conversation. Cannot be used during player conversations.
</details>

<details markdown="1">
<summary> <code>record_relationship_change</code> — Update relationship</summary>

**Class:** `RecordRelationshipChange`

Updates relationship memory toward another citizen or the player.

**Parameters:** `citizen_name` (optional), `change` (number, -1.0 to 1.0), `type` (enum)

*Used when a citizen decides they like or dislike someone.*
</details>

<details markdown="1">
<summary> <code>add_event_to_memory</code> — Add memory event</summary>

**Class:** `AddEventToMemory`

Adds an event to the citizen's memory.

**Parameters:** `event` (string)

Disabled when compaction mode is on.

*Used when something notable happens during a conversation.*
</details>

<details markdown="1">
<summary> <code>recommend_job</code> — Job recommendations</summary>

**Class:** `RecommendJobAction`

Analyzes citizen's skills and returns top 5 job recommendations with scores. No parameters.

*Used when you ask "what job should I do?"*
</details>

<details markdown="1">
<summary> <code>get_current_situation</code> — Current activity</summary>

**Class:** `GetCurrentSituationAction`

Refreshes knowledge of the citizen's current activity, work status, blocked items, health. No parameters.

*Used to ground the AI in what the citizen is actually doing right now.*
</details>

---

## Player-Only Tools

<details markdown="1">
<summary> <code>drop_item</code> — Drop item</summary>

**Class:** `DropItemAction`

Drops an item from the citizen's inventory.

**Parameters:** `slot_index` (integer), `count` (integer, -1 for entire stack)
</details>

<details markdown="1">
<summary> <code>leave_colony</code> — Leave colony (permanent)</summary>

**Class:** `LeaveColonyAction`

Citizen permanently leaves the colony and becomes a recruitable visitor. Requires a tavern building. **Final decision.**

!!! warning "Irreversible"
 This action is permanently irreversibly — once a citizen leaves, they're gone from your colony.
</details>

<details markdown="1">
<summary> <code>initiate_broadcast</code> — Broadcast message</summary>

**Class:** `InitiateBroadcastAction`

Records a message for colony-wide broadcast.

**Parameters:** `message` (string)

Disabled when broadcast is off.

*Ask your citizen "can you spread the word?" during a conversation.*
</details>

---

## Tool Registration

All tools are registered in `AITools.register()`. The `disabledTools` config option can remove specific tools from the AI's available set.

### Disabling Tools

To disable a tool, add its name to the `disabledTools` list in the config:

```json5
"disabledTools": ["leave_colony", "drop_item"]
```

!!! tip "Safety first"
 Many users disable `leave_colony` and `drop_item` to prevent accidents. The AI won't use these tools if they're disabled.
