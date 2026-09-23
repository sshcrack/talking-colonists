# Track Q — Quality of life

Player- and server-owner-facing fixes in core. Paths are relative to
`src/main/java/me/sshcrack/mc_talking/`.

| Task | Summary | Depends on | Size | Wave | Existing issue |
| --- | --- | --- | --- | --- | --- |
| R1 | Finish roadmap task 12 (release verification) | — | S | 0 | — |
| Q1 | Missing API key onboarding | — | S | 0 | — |
| Q2 | Quota exhaustion visible to players | — | S | 0 | — |
| Q3 | Localize hard-coded strings | — | S | 0 | — |
| Q4 | Ambient speech budget per player | — | S | 0 | #133 |
| Q5 | Complaint ramp by duration and colony age | — | M | 1 | #125 (merges #117, #121–#124) |
| Q6 | Building style is not a complaint | — | XS | 0 | #54 |
| Q7 | Archetype picker in the config screen | — | M | 1 | #128 (supersedes #127) |
| Q8 | Config presets | Q4, Q7 | M | 2 | — |
| Q9 | Start conversations without the device | — | S | 0 | — |
| Q10 | Type in chat to the citizen you are talking to | A4 | S | 2 | — |
| Q11 | Incremental refactor of god classes | — (opportunistic) | L | — | #116 |

---

## R1 — Finish roadmap task 12

### Agent prompt

Complete the remaining acceptance of `roadmap/12-addon-migration.md`: confirm Gemini Live Library
2.4.0 is published for both loader coordinates, build against the published artifact (not the
composite build), record the credentialed in-world matrix, and mark task 12 Complete in
`roadmap/README.md`.

---

## Q1 — Missing API key onboarding

### Agent prompt

Today a missing key only logs an error at server start (`ServerEventHandler.java:76`); players
learn about it when the talking device prints `mc_talking.no_key`. When an operator joins a
server without a key, send one chat message with a clickable link to
https://aistudio.google.com/apikey and, on the integrated server, a clickable action that opens the
YACL config screen. Show it once per session per operator. Non-operators see nothing. Also make
automatic features (greetings, mumbling) log the missing key once instead of silently skipping.

### Acceptance

- Translatable message; only operators receive it; never repeated within a session.

---

## Q2 — Quota exhaustion visible to players

### Agent prompt

`config/QuotaTracker` and `TtsQuotaManager` know when a model is rate-limited, but players only
see silence and operators see raw errors (`GeminiWsClient.java:807`). When a player-initiated
conversation fails for quota, show a translated action-bar message ("Citizens are out of breath —
the Gemini quota is used up") instead of a raw error. Add per-model quota state and the estimated
reset time to `/talking_colonists status`. Keep the raw error for operators behind the existing
config toggle. This task also cleans up the quota state that A7 exposes.

### Acceptance

- Tests for quota state transitions; manual check of action-bar text for a forced quota error.

---

## Q3 — Localize hard-coded strings

### Agent prompt

Replace `Component.literal` user-facing messages with translation keys in
`item/ConversationCreatorDevice.java`, `item/MumblingTriggerDevice.java`,
`manager/CitizenWsClient.java`, and any other non-debug call site. Debug commands may stay English.
Add a short "Translating Talking Colonists" section to the README explaining how to contribute
a `lang/<locale>.json` (Portuguese players have asked).

### Acceptance

- `grep -rn 'Component.literal("' src/main/java` finds only debug commands and dynamic values.

---

## Q4 — Ambient speech budget per player (#133)

### Agent prompt

When many citizens stand together, greetings, mumbles, and casual contacts stack up. Add a
per-player budget: at most N ambient lines (greetings, mumbles, voiced rumors/broadcasts) that a
player can hear per rolling minute, configurable, with a default that feels calm in a busy
colony. Apply it in the shared path that selects ambient speakers rather than in each handler.
Urgent contacts and player-started conversations are exempt.

### Acceptance

- Tests with a fake clock: budget enforced across handlers; exempt kinds unaffected; config
  change applies without restart. `en_us.json` updated.

---

## Q5 — Complaint ramp by duration and colony age (#125)

Merges #117, #121, #122, #123, #124 — one design instead of six issues.

### Agent prompt

Track, per citizen, when each negative happiness factor started (persisted with the citizen's
memory data). Scale how strongly the prompt expresses it by duration tiers (for example: mild
remark → complaint → demand) and soften housing complaints while the colony is young (use the
existing colony age/foundation tracking). Never change MineColonies' own happiness values — only
the prompt wording and urgent-contact weight. Tiers and grace periods are configurable.

### Acceptance

- Tests: a new homeless citizen in a new colony starts at the mildest tier; tiers escalate with
  time; resolving the factor resets its timer; persistence across reload.
- Close #117, #121–#124 as duplicates referencing this task.

---

## Q6 — Building style is not a complaint (#54)

### Agent prompt

Citizens criticise cavern (and other) building styles as if they were poor housing. Add a prompt
fact that the building style is the colony's chosen aesthetic and is not a sign of quality; base
housing complaints only on building level and happiness factors.

### Implementation
Added clear instruction to `DefaultCitizenPromptProvider.appendDetailedHappinessState()` directing the model that building style is aesthetic choice and must not be complained about.

---

## Q7 — Archetype picker in the config screen (#128)

### Agent prompt

Show the built-in `config/PersonalityArchetype` values as toggles in the YACL personality group
(which ones are in the random pool), keep the free-text list for custom archetypes, and add a
short description per archetype. Existing configs must keep today's behaviour (all built-ins
enabled). #127 was closed as completed but is not implemented; this task covers it.

---

## Q8 — Config presets

**Depends on:** Q4, Q7

### Agent prompt

The config has about 60 chance, interval, and range entries. Add a preset selector at the top of
the config screen — *Free tier* (conservative sessions and ambient chatter), *Paid key* (more
concurrent sessions and chatter), *Quiet colony* (player conversations only) — that writes the
relevant values. Values edited afterwards mark the preset as *Custom*. Document which keys each
preset sets.

---

## Q9 — Start conversations without the device

### Agent prompt

Conversations currently need the `talking_device` item. Add an optional, config-gated way to start
one by sneak + right-clicking a citizen with an empty hand (MineColonies uses plain right-click for
its own GUI), plus an optional client keybind that talks to the citizen under the crosshair. Both
go through `startPlayerConversation` so all eligibility checks still apply.

---

## Q10 — Type in chat to the citizen you are talking to

**Depends on:** A4

### Agent prompt

While a player is in a conversation, chat messages starting with a configurable prefix (default
`@`) — or all messages when a per-player toggle is on — go to the citizen through A4's
`sendPlayerText` and are not broadcast to server chat. Show the typed line locally so the player
sees what they sent. This helps players without a microphone and in noisy rooms.

---

## Q11 — Incremental refactor (#116)

Not a scheduled task. When another task touches `ConversationManager` or another class listed in
#116, extract the part it changes into an internal module with tests. Never block feature work on it.

**This is an internal refactor only — it must not break addons.** Guardrails:

- Nothing under `src/api` changes as part of Q11. If a refactor seems to need an API change, it
  becomes a separate, additive Track A task.
- Add a binary-compatibility check to CI (for example japicmp against the last published
  `mc_talking-api` artifact) so an accidental API break fails the build instead of reaching addons.
- Keep `config.McTalkingConfig` in place with its `INSTANCE` handler and field names. Field names
  are also the config file keys, and Colonist Errands 3.0 reads `geminiApiKey`, `currentAiModel`,
  `hasGeminiApiKey()`, `load()` and `blockingTaskUrgencyMultiplier` by reflection (fail-soft).
- Keep `internal.tool.AiToolRuntime.findById(...).providerName()` until Errands switches to the
  public `AiToolRegistry.providerName(addonId, tool)`, which already returns the same name.
- Before merging a refactor, grep the pinned Colonist Errands source for `me.sshcrack.mc_talking`
  outside `.api.` and confirm those reflective targets still resolve.
