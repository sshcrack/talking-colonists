# Track A — API gaps

Analysis of `src/api` (API generation 2, 2.0.0-beta.2) against the addon ideas in
[addons.md](addons.md). Paths are relative to `src/api/java/me/sshcrack/mc_talking/api/` for the
API and `src/main/java/me/sshcrack/mc_talking/` for the runtime.

## What already exists

| Need | Supported by |
| --- | --- |
| Inject facts into prompts | `prompt/CitizenPromptService.registerContributor` |
| Addon tools with permission checks | `tool/AiToolRegistry`, `AiToolContext` |
| Read/write citizen memory, confirmed outcomes | `memory/CitizenMemoryService` |
| Read broadcasts and rumors a citizen knows | `CitizenMemorySnapshot.broadcasts()/rumors()` |
| Start a player conversation | `CitizenConversationService.startPlayerConversation(player, citizen)` |
| One audible line from a citizen | `CitizenConversationService.requestAmbientLine` |
| Meetings: turns, agenda, player statements, transcript | `ControlledConversationSession` |
| Automatic group discussion | `AutonomousDiscussionHandle/Policy` |
| Reserve citizens for addon gameplay | `CitizenConversationService.reserveActivity` |
| Speech and urgency policies | `CitizenConversationRules` |
| Conversation start/end observation | `ConversationLifecycleListener` (STARTED/ENDED only) |

## Gap summary

| Task | Gap | Evidence | Unblocks | Size |
| --- | --- | --- | --- | --- |
| A0 | Addons cannot detect which 2.x features a runtime has | `TalkingColonistsApi` has only `API_MAJOR_VERSION = 2` | every addon targeting 2.1 | S |
| A1 | Broadcasts can be read but not created | only `InitiateBroadcastAction` (AI tool) creates `ColonyBroadcast`; no API | X1, X2, X3 | M |
| A2 | Colony events are internal | `util/ColonyEventBuffer` is not exposed; no listener | X2, X3, X7 | M |
| A3 | No text-only in-character generation | every speech API is audible (`requestAmbientLine`, sessions) | X1, X2, X3, X4, X7, X8, X10 | M |
| A4 | No typed player input into a live conversation | no chat handler; `addPlayerStatement` exists only on controlled sessions | Q10, X10, X11 | M |
| A5 | No per-utterance events outside controlled sessions | lifecycle events carry no text; `GeminiWsClient.onInputTranscription/onOutputTranscription` are internal | X3, X7, X9, X10 | M |
| A6 | Player conversations cannot be scoped by an addon | `startPlayerConversation(player, citizen)` takes no context or tool allow-list | A4, X5, X7, X10, X11 | S |
| A7 | No quota, capacity, or config view for scheduling costly work | `config/QuotaTracker` is internal; only `hasAmbientCapacity` is public; Errands reads `McTalkingConfig` by reflection | X2, X6, L1 | S |
| A8 | Visitors cannot speak | `ConversationManager.java:596` rejects `VisitorCitizen` | X5 | M |
| A9 | Multi-colony controlled sessions are undefined | no documented or tested behaviour for mixed-colony attendees | X8 | S |
| A10 | No speech-to-text without a citizen | microphone audio only flows into citizen sessions | X1 (voice loudspeaker) | L |
| A11 | 2.1 docs, examples, and release | — | publishing addons against 2.1 | S |

---

## A0 — API feature detection

**Depends on:** — · **Size:** S · **Wave:** 0

### Agent prompt

Add additive feature detection so addons compiled against API 2.1 can run on 2.0 runtimes and
degrade gracefully. Add `TalkingColonistsApi.API_MINOR_VERSION` and a
`TalkingColonistsApi.supports(ApiFeature)` query backed by the runtime. `ApiFeature` is an enum with
one constant per Track A task (`BROADCAST_PUBLISHING`, `COLONY_EVENTS`, `TEXT_GENERATION`, …); each
task flips its constant on when it lands. Calling an unsupported feature's entry point must fail
with a documented `UnsupportedOperationException` rather than a `NoSuchMethodError`.

### Acceptance

- `supports` returns false for every feature on a runtime that predates it (simulate with a fake
  `Services` implementation in tests).
- `docs/addon-api.md` shows the detection pattern; an `apiTest` example compiles for both loaders.

### Implementation record — 2026-09-24

- Added `ApiFeature` (one constant per A1–A10), `TalkingColonistsApi.API_MINOR_VERSION = 1`,
  `runtimeApiMinorVersion()`, `supports(ApiFeature)`, and `requireSupported(ApiFeature)` (standard
  `UnsupportedOperationException` for unimplemented entry points). New `Services` methods are
  default methods, so older implementations answer "minor 0 / unsupported" instead of throwing.
  The runtime backend reports minor 1 and no supported features yet.
- `docs/addon-api.md` documents feature detection, including the guard addons need on 2.0 runtimes
  (where `supports` itself does not exist yet). `AddonApiCompileExample` uses it.
- `ApiFeatureDetectionTest`: fake `Services` without the new methods reports every feature
  unsupported and minor 0; the real backend reports minor 1 and nothing supported.
- `./gradlew test` passed on both loaders. Purely additive; no existing API member changed.

---

## A1 — Broadcast and news publishing

**Depends on:** A0 · **Size:** M · **Wave:** 1

### Agent prompt

Expose creation of colony broadcasts, which today only the `initiate_broadcast` AI tool can do.
Add `CitizenMemoryService.publishBroadcast(IColony, BroadcastRequest)` returning a typed result.
`BroadcastRequest` carries message, source (player UUID, addon namespace, or block position),
scope (`COLONY_IMMEDIATE` — every citizen learns it now; `PROPAGATE_FROM` — spreads from a citizen
or position through `BroadcastPropagationService`), optional expiry, and whether nearby citizens
may announce it aloud. Add `retractBroadcast(id)`. Reuse `ColonyBroadcast` and the existing
propagation, voicing, and prompt-inclusion limits; do not create a second system.

Record provenance through the existing `MemoryProvenance` so prompts can say "the notice board
says…" rather than attributing it to a player. Bound message length and per-colony publish rate.

### Acceptance

- Tests cover both scopes, retraction, expiry, rate limits, provenance, and persistence across
  save/load; the AI tool path routes through the same runtime.
- A broadcast published with `COLONY_IMMEDIATE` appears in every citizen's next prompt snapshot.

### Implementation record — 2026-09-24

- API (additive, `ApiFeature.BROADCAST_PUBLISHING` now supported): `CitizenMemoryService.publishBroadcast(IColony,
  BroadcastRequest)` → `BroadcastPublishResult` and `retractBroadcast(IColony, id)`, backed by default methods on
  `MemoryService`. New `BroadcastRequest` (factories `immediate` / `fromCitizen` / `fromPosition`, `withExpiry`,
  `withAnnounceAloud`), `BroadcastSource` (`player` / `addon` / `block`), `BroadcastScope`.
  `CitizenBroadcastMemoryView` gained `provenance`, `sourceLabel`, `expiresAtMs`; the 2.0 constructor is kept.
  `checkApiCompatibility` reports only additions.
- Runtime: `broadcast/BroadcastPublisher` is the single path for the API and the `initiate_broadcast` tool
  (validation, 10 per 10 minutes per colony, provenance). `ColonyBroadcast` persists provenance, source label,
  expiry and the announce flag; old saves load as player broadcasts. Propagation purges expired broadcasts
  and only announces broadcasts that allow it; prompts say "<source> announced: ..." for non-player sources.
- Tests: `BroadcastPublisherTest` covers both scopes, retraction, expiry, rate limits, provenance,
  save/load plus legacy NBT, request validation, and prompt inclusion after `COLONY_IMMEDIATE`.

---

## A2 — Colony event feed

**Depends on:** A0 · **Size:** M · **Wave:** 1

### Agent prompt

Expose `util/ColonyEventBuffer` through a public `ColonyEventService`: `recent(colony, maxAge)`
returning immutable typed views, `registerListener(id, order, listener)` for newly recorded
events (delivered on the server thread), and `record(colony, AddonColonyEvent)` so addons can add
namespaced events such as "election won". Addon events must appear in prompts under the existing
lifecycle-event duration setting and must be distinguishable from core events. Keep the buffer's
bounds; addon events count against a separate per-namespace budget.

### Acceptance

- Tests cover listener ordering, exceptions in one listener not affecting others, addon event
  bounds, persistence, and prompt inclusion.

### Implementation record — 2026-09-24

- API (additive, `ApiFeature.COLONY_EVENTS` now supported): `api.colony.ColonyEventService.recent(colony, maxAge)`,
  `record(colony, AddonColonyEvent)`, `registerListener(id, order, ColonyEventListener)`; `ColonyEventView`,
  `ColonyEventType` (core kinds + `ADDON`), `AddonColonyEvent` (namespace, key, description ≤ 300 chars).
  `api.service.ColonyEventFeedService` is exposed through a default `Services.colonyEvents()`.
- Runtime: `ColonyEventBuffer` events carry addon namespace/key (persisted; old saves load unchanged; addon
  events without a namespace are dropped on load). `trimEvents` keeps 20 core events plus 10 per addon
  namespace. Every recorded event, core or addon, is dispatched to ordered listeners with per-listener error
  isolation. The persistence mixin is untouched (it only calls `serialize`/`deserialize`/`trimEvents`).
- Tests: `ColonyEventBufferTest` (budgets, per-namespace isolation, listener order and failure isolation,
  unregistering, save/load incl. legacy and corrupt addon events, API type parity, validation) and GameTest
  `addonColonyEventReachesListenersFeedAndPrompt` (real colony: listener, `recent()`, and the citizen's prompt
  context), passing on both loaders.

---

## A3 — Text-only in-character generation

**Depends on:** A0 · **Size:** M · **Wave:** 1

### Agent prompt

Add `CitizenTextService.generate(citizen, TextRequest)` returning
`CompletableFuture<TextResult>`. The request carries a directive, optional max length, optional
JSON schema for structured output (reuse the structured-output support from roadmap task 02),
and a purpose tag used for logging and quota accounting. Use the citizen's normal prompt snapshot
(including contributors and the configured response language) and the Flash model. Run on the
background capacity pool, respect `QuotaTracker`, and return typed failures (`QUOTA`,
`NO_CAPACITY`, `INVALID_OUTPUT`, `CANCELLED`). Add a colony-voice variant with no single citizen
(for newspapers and notice replies) that uses the colony prompt view only.

### Acceptance

- Tests use fake transport: plain text, structured JSON with validation failure, quota
  exhaustion, cancellation, and language instruction present in the request.
- Generated text never triggers audio or occupies a foreground slot.

### Implementation record — 2026-09-24

- API (additive, `ApiFeature.TEXT_GENERATION` now supported): `api.text.CitizenTextService.generate(citizen, TextRequest)`
  and `generateColonyVoice(colony, TextRequest)` → `CompletableFuture<TextResult>`; `TextRequest` (purpose tag,
  directive, `withMaxChars`, `withResponseSchema`), `TextResult` with typed statuses (`QUOTA`, `NO_CAPACITY`,
  `INVALID_OUTPUT`, `CANCELLED`, plus `UNAVAILABLE` and `PROVIDER_ERROR`). New `TextService` is exposed through a
  **default** `Services.text()`, so older `Services` implementations stay compatible.
- Deviation from the prompt: requests do **not** use the Live background slot pool. They are plain Flash calls, so
  borrowing Live slots would only starve greetings and compaction without protecting the Live concurrency limit.
  `TextGenerationRuntime` has its own limit of 2 concurrent requests and uses `QuotaTracker` for the Flash model.
- Prompts: `TextPrompts.citizen` = `PromptRuntime.getDetailedCitizenInfoPrompt` (with contributors) + writing rules
  and the response language; `TextPrompts.colony` = `CitizenPromptViewFactory.createColonyView` (extracted from the
  citizen view builder, so both share one code path) + colony-voice rules.
- Tests: `TextGenerationRuntimeTest` (fake transport, manual executor): plain text, language in the prompt, colony
  prompt, structured JSON incl. code fences, missing required field and non-JSON, quota fast-fail and provider 429,
  no credential leak in errors, bounded concurrency, caller and server-stop cancellation, missing key, trimming.
  Live: `PromptBehaviourLiveTest.textGenerationReturnsStructuredPortuguese` passes against Gemini.

---

## A4 — Player text input into conversations

**Depends on:** A0, A6 · **Size:** M · **Wave:** 2

### Agent prompt

Add `CitizenConversationService.sendPlayerText(player, citizen, text)` which delivers typed text
into that player's active conversation as a user turn (Live sessions accept text parts) and
records it in the transcript with player provenance. Also add `addContext(player, citizen, note)`
for addon-originated system notes during a live player conversation (for example "the player just
handed you the deed"). Enforce the authenticated player (never the model), length limits, and a
rate limit. Q10 builds the chat UX on top of this.

### Acceptance

- Tests: text reaches the provider as a user turn, is attributed to the right player, is rejected
  for a different player's conversation, and counts toward memory extraction.

---

## A5 — Conversation utterance events

**Depends on:** A0 · **Size:** M · **Wave:** 2

### Agent prompt

Extend lifecycle observation with an `UTTERANCE` event for every conversation kind: speaker kind
(player, citizen, system), speaker id, final transcript text, session id, turn id, and whether
the text came from transcription or typed input. Source it from the existing
`onInputTranscription` / `onOutputTranscription` callbacks and the Flash+TTS script path. Emit
only finalised utterances (not partial transcription chunks) on the server thread. Document that
transcription is best-effort and must not be treated as proof for irreversible gameplay; addons
should confirm through their own tools.

### Acceptance

- Tests with fake transport cover player and citizen utterances in player, pair, and controlled
  sessions, no duplicates for chunked transcription, and no events after a session ends.

### Implementation record — 2026-09-24

- API (additive, `ApiFeature.UTTERANCE_EVENTS` now supported): `ConversationUtteranceEvent` (kind, citizen,
  speaker PLAYER/CITIZEN/SYSTEM, speaker ID and name, text, session/turn IDs, source
  TRANSCRIPTION/TYPED/SCRIPT, game time) and `CitizenConversationService.registerUtteranceListener`, backed by
  a default `ConversationService` method. It is a separate listener rather than a new lifecycle `Phase`
  constant, because an added enum constant could break addons that switch over `Phase` exhaustively.
- Runtime:
  - `UtteranceTracker` (one per provider session) collects input transcription chunks and emits a single
    player utterance when the provider completes the turn. It emits the citizen utterance when that turn's
    audio has been heard in full, and emits nothing after the session closes.
  - `ConversationManager.emitClientUtterance` reads the kind, player and controlled IDs from the foreground
    registry at that moment, and drops the event if the client is no longer the citizen's current session.
    Delivery is on the server thread.
  - Citizen Live sessions now request input audio transcription. Only the diagnostic `INPUT_OBSERVED`
    progress reads it, so microphone turn handling is unchanged.
  - Flash/TTS pair conversations emit each script line (`ScriptUtterances`, lines after the "Transcript"
    heading) once playback finished. Live pair clients go through the shared `GeminiWsClient` path.
    Controlled-session `addPlayerStatement` emits a TYPED player utterance.
- Tests: `UtteranceTrackerTest` (chunked input → one utterance, one per turn, citizen-only turns, nothing
  after end), `ScriptUtterancesTest`, and GameTest `controlledPlayerStatementIsAnUtterance`. No fake Live
  transport exists yet; the tracker is the transport-independent part and is tested directly.

---

## A6 — Player conversation start options

**Depends on:** A0 · **Size:** S · **Wave:** 1

### Agent prompt

Add `startPlayerConversation(player, citizen, PlayerConversationOptions)` where options carry a
session agenda/context (via `PromptSessionContext`), an addon tool allow-list (same semantics as
`ControlledConversationOptions`), a purpose tag visible in lifecycle events, and whether normal
memory extraction runs. Addons use it to open "quest giver", "judge", or "tour guide"
conversations without replacing global prompts. Session context must not leak into later
conversations.

### Acceptance

- Tests: context appears only in that session; tool allow-list is enforced at execution time;
  default overload behaves exactly as today.

### Implementation record — 2026-09-24

- API (additive, `ApiFeature.PLAYER_CONVERSATION_OPTIONS` now supported):
  `CitizenConversationService.startPlayerConversation(player, citizen, PlayerConversationOptions)`, backed by a
  default `ConversationService` method that accepts only default options on older runtimes. New
  `PlayerConversationOptions` (agenda ≤ 2000 chars, `ControlledConversationOptions` tools, purpose
  `[a-z0-9_.:-]{1,64}`, `extractMemory`; `defaults()` plus `with…` methods). `ConversationLifecycleEvent` gained
  `purpose`; the 2.0 constructor is kept.
- Runtime: the options become the player client's `PromptSessionContext`, which lives only on that client. The
  agenda is added to that session's system prompt, and prompt contributors see it through the context. The
  existing `GeminiWsClient` gate enforces the allow-list when the model calls a tool. `extractMemory=false`
  skips the conversation summary. Non-default options never take over a mumbling session: they replace it,
  so the agenda is in the prompt from the start. The purpose is reported on this session's STARTED and ENDED
  events.
- Tests: `PlayerConversationOptionsTest`, `PlayerSessionContextsTest` (the agenda reaches only its own
  session, allow-list semantics, and defaults equal an ordinary conversation).

---

## A7 — Capacity and quota status

**Depends on:** A0, Q2 · **Size:** S · **Wave:** 2

### Agent prompt

Expose a read-only `ProviderBudgetService`: foreground and background slots in use / available,
per-model quota state (`OK`, `EXHAUSTED_UNTIL(time)`, `UNKNOWN`) from `QuotaTracker`, and TTS quota
from `TtsQuotaManager`. Add a listener for quota state changes. Also expose the read-only
configuration addons currently read by reflection (whether an API key is set, the selected model,
the urgent-contact blocking multiplier) and a `reloadConfig()` entry point, so Colonist Errands can
drop its reflective access to `McTalkingConfig`. Addons use this to schedule
expensive work (nightly newspaper, campfire nights) and to show honest "citizens are tired" UI.

### Acceptance

- Tests cover state transitions, listener delivery, and that the view is immutable.

### Implementation record — 2026-09-24

- **API** (additive, `ApiFeature.PROVIDER_BUDGET` now supported):
  - `api.provider.ProviderBudgetService` with `snapshot()`, `config()`, `registerQuotaListener` and
    `reloadConfig()`, backed by a default `Services.providerStatus()`.
  - Views: `ProviderBudgetView` (foreground and background `SlotUsage`, the quota of the Live model, the
    text model and `"tts"`), `ModelQuotaView` (`OK` / `EXHAUSTED` with an optional `exhaustedUntil` /
    `UNKNOWN` when no API key is set), and `ProviderConfigView` (API key set, Live model, text model,
    blocking-task urgency multiplier).
  - The key itself is never exposed.
- **Runtime:** `internal/provider/ProviderBudgetRuntime` reads through a `Sources` interface. The server
  tick polls it once a second and notifies listeners of each changed model on the server thread, so an
  expiring backoff also produces an event. `reloadConfig()` reloads the YACL config from disk.
- **Tests:** `ProviderBudgetRuntimeTest` covers:
  - slots and models in the snapshot
  - `UNKNOWN` without a key
  - one event per transition, including the recovery
  - a failing listener does not block the others
  - immutable views

---

## A8 — Visitor speakers

**Depends on:** A0 · **Size:** M · **Wave:** 1

### Agent prompt

Allow `VisitorCitizen` entities as conversation participants behind an opt-in speech policy
(default off for core ambient chatter). Build a visitor prompt view from `IVisitorData`: name,
skills, recruitment cost, time in the tavern, no job/home/family. Visitors have no persistent
citizen memory; keep a bounded short-term memory that migrates to citizen memory if they are
recruited. Update `ConversationEligibility` so the `VISITOR` rejection only applies when no
policy allows visitors. Keep the talking device's current visitor message unless a policy allows it.

### Acceptance

- Tests: visitors rejected by default; allowed with a registered policy; recruited visitor keeps
  its short-term memory; prompt view contains no citizen-only fields.

### Implementation record — 2026-09-24

- **API** (additive, `ApiFeature.VISITOR_SPEAKERS` now supported):
  - `VisitorSpeechPolicy` and `CitizenConversationRules.registerVisitorPolicy`, backed by a default
    `ConversationRuleService` method.
  - `VisitorPromptView(recruitCost, daysInColony)`, exposed as a default `CitizenPromptView.visitor()`.
- **Eligibility:** the `VISITOR` rejection now applies only when no visitor policy allows the requested
  kind. Veto policies still run afterwards. The talking device and the talk gesture keep their visitor
  messages unless a policy allows `PLAYER`.
- **Prompt view:** a visitor view has no job, home, workplace, family, requests, quests or happiness
  modifiers, and its housing status is `UNKNOWN`. It carries the recruit cost and the days since Talking
  Colonists first saw the visitor, since MineColonies records no arrival time. The prompt introduces the
  visitor as a traveller at the tavern and never adds the "no job" or "no home" complaint lines.
- **Memory:** visitors keep at most 10 facts and 10 events. `VisitorData` extends `CitizenData`, so the
  memory is saved through the existing mixin. MineColonies' recruitment copies the visitor NBT into the
  new citizen, so the memory moves with a recruit without further code.
- **Tests:** GameTests `visitorsSpeakOnlyWithAPolicy`, `visitorPromptViewHasNoColonistFields` and
  `recruitedVisitorKeepsItsMemory`, which copies visitor NBT the way `RecruitmentInteraction` does.
  `CitizenMemoriesVisitorTest` covers the memory bound and the saved first-seen day.

---

## A9 — Cross-colony controlled sessions

**Depends on:** A0 · **Size:** S · **Wave:** 1

### Agent prompt

Define and test controlled sessions whose attendees belong to different colonies. Each speaker
keeps its own colony prompt view; add each other colony's name and the diplomatic relation
(already used by the colony connection prompt) to the session context. Decide and document which
colony's tools and permissions apply to a turn (the speaker's). Reject attendees in different
dimensions with a typed failure.

### Acceptance

- Tests: mixed-colony session runs turns with correct per-speaker context; tool permissions follow
  the speaker's colony; cross-dimension attendees are rejected.

### Implementation record — 2026-09-24

- API (additive, `ApiFeature.CROSS_COLONY_SESSIONS` now supported): `ControlledSessionRejectedException`
  (extends `IllegalArgumentException`, `Reason.CROSS_DIMENSION`), documented on `createControlledSession`.
- Runtime: `ControlledConversationRuntime.Hooks` gained default `colony`, `dimension` and `relation` methods.
  Session creation rejects attendees in different dimensions. Each turn prompt lists the other colonies'
  attendees and the speaker's colony's diplomacy status toward each colony, and says that tools and
  permissions apply only to the speaker's own colony.
- Decision: the speaker's colony applies. A turn runs in the speaker's own provider session, built from the
  speaker's citizen data, so tools and permission checks resolve against the speaker's colony. Nothing
  dispatches a tool to another colony.
- Tests: `ControlledConversationRuntimeTest` covers four cases:
  - per-speaker cross-colony context in both directions
  - own-colony attendees not listed as foreign
  - the turn starts in the speaker's own session
  - one-colony sessions unchanged, unknown relations, and the typed cross-dimension rejection

---

## A10 — Player speech capture

**Depends on:** A0 · **Size:** L · **Wave:** 2

### Agent prompt

Add `PlayerSpeechService.capture(player, maxDuration)` returning
`CompletableFuture<SpeechCaptureResult>` with the transcript of what the player says through
Simple Voice Chat, without any citizen. Reuse `MicrophoneTurnModule` and `PcmSpeechDetector` for
end-of-speech detection, and a text-only transcription request. Require an explicit player
action to start capture (never always-on), show a client indicator while capturing, bound
duration, and respect quota. Used by a loudspeaker/microphone item.

### Acceptance

- Tests with fake audio: speech → transcript, silence timeout, cancellation, player disconnect.
- Manual in-world check recorded: capture indicator visible, no audio captured after the end.

---

## A11 — API 2.1 documentation and release

**Depends on:** A1–A10 · **Size:** S · **Wave:** 3

### Agent prompt

Update `docs/addon-api.md` and `docs/addon-migration.md` for every 2.1 addition, add an `apiTest`
example per feature, bump `API_MINOR_VERSION`, publish the developer API artifact, and write
release notes that list each feature with its `ApiFeature` constant. Tasks that did not land are
listed as unsupported rather than hidden.

### Acceptance

- `./gradlew buildAndCollect` and all `apiTest` examples pass on both loaders; the published API
  artifact matches the runtime.

---

## L1 — AI backend interface (exploratory)

**Depends on:** A3, A7 · **Size:** XL · **Wave:** 3 · **Issue:** #131

### Agent prompt

Investigate an internal backend interface covering Live audio sessions, Flash text, and TTS so a
local backend (for example Whisper + a local LLM + Piper) could be added later, as an addon or
in core. Produce a design document with the minimum interface, which features degrade without
Live audio (barge-in, voice consistency), and the migration cost. Do not implement a local
backend in this task.

### Acceptance

- Design document merged under `docs/`; #131 updated with the outcome.
