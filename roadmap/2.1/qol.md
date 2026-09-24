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


### Implementation record — 2026-09-24

- `handler/MissingApiKeyOnboardingHandler` + `onboarding/MissingKeyOnboardingTracker`: on login, an
  operator (permission ≥ 2, or the single-player/LAN owner) without a configured key gets one
  translated message with a clickable AI Studio link, once per player per server session. On an
  integrated server it also offers "open config", which runs the client-only command
  `/mc_talking_open_config` (registered in `McTalkingClient`) to open the YACL screen.
- `onboarding/MissingApiKeyLogger`: automatic handlers (greetings, urgent contact, random
  conversations, pregeneration) log the missing key once instead of silently returning.
- Tests: `MissingKeyOnboardingTrackerTest`, `MissingApiKeyLoggerTest`; `./gradlew test` passed on both loaders.
- Manual check pending: clicking "open config" in chat opens and keeps the config screen on both loaders.
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

### Implementation record

Done on `roadmap/q2-quota-visibility`. `config.QuotaTracker` and `config.TtsQuotaManager` now
expose their state as an immutable `config.QuotaSnapshot` (`model`, `status` — `OK`/`EXHAUSTED` —,
`sinceMs`, nullable `resetAtMs`), designed so A7's read-only `ProviderBudgetService` can wrap
`QuotaTracker.snapshot(model)`/`snapshotAll()` and `TtsQuotaManager.snapshot()` without further
internal changes. `resetAtMs` is only populated from a provider-supplied retry hint
(`QuotaRetryInfo.parseRetryDelayMs`, matched against `google.rpc.RetryInfo`'s `retryDelay` in a
raw Gemini error body) — our own progressive backoff is a local guess and is deliberately not
surfaced as an authoritative estimate. The Live session quota path (`GeminiWsClient`/
`GeminiLiveClient`) only gets a close-reason string, not a structured body, so its reset time is
"unknown" today; TTS/Flash HTTP failures (`UnexpectedResponseException`) can populate it when the
API includes one.

`CitizenWsClient.onQuotaExceededEvent` now shows every player in the failed conversation a
translated action-bar message (`mc_talking.quota_exceeded_actionbar`) via
`config.QuotaPlayerMessageThrottle` (per-player 30s window, fake-clock-testable). The raw
diagnostic message to OP players is now also gated by `sendErrorsToPlayers` (previously
unconditional for quota, unlike the sibling error-path). `/talking_colonists status` lists a
quota line per `AvailableAI` model plus one for TTS, each with state and a formatted reset
estimate or "unknown".

New tests: `QuotaTrackerTest`, `TtsQuotaManagerTest`, `QuotaPlayerMessageThrottleTest` (all with
fake clocks) covering exceeded→reset transitions, per-model/TTS independence, progressive
backoff, provider retry-delay parsing, and the per-player message throttle window.

Not done here (left for A7): no public API wrapper was added; `QuotaSnapshot`/`QuotaStatus` are
internal types in `config`, addressed only for A7 to reuse read-only.

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

### Implementation

Replaced 9 user-facing hard-coded messages in ConversationCreatorDevice, MumblingTriggerDevice, CitizenTalkingDevice, and CitizenWsClient with Component.translatable() calls using new keys in en_us.json. Updated all enum display names (ModalityModes, ConversationMode, MemoryMode, AvailableAI) to use localization. Verified that only 2 dynamic-value Component.literal() calls remain. Added translation contributor guide to README.

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

**Internal classes can change freely; the public API surface cannot break.** Guardrails:

- Refactors may add to `src/api` when useful, but must not remove, rename, or change existing public
  API members. Any breaking API change needs the maintainer's approval first.
- Add a binary-compatibility check to CI (for example japicmp against the last published
  `mc_talking-api` artifact) so an accidental API break fails the build instead of reaching addons.
- Heads-up, not a ban: Colonist Errands 3.0 still reads a few internals by reflection and falls back
  softly if they move — `config.McTalkingConfig` (`INSTANCE`, `geminiApiKey`, `currentAiModel`,
  `hasGeminiApiKey()`, `load()`, `blockingTaskUrgencyMultiplier`) and
  `internal.tool.AiToolRuntime.findById(...).providerName()`. If a refactor moves them, mention it in
  the release notes; A7 and the public `AiToolRegistry.providerName(addonId, tool)` are the supported
  replacements. Renaming config fields also changes config file keys for users.
