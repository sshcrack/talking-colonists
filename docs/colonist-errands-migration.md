# Colonist Errands integration audit

Reviewed source: `Lovkar-Squid/colonist-errands` commit
`f270362aca847726087213c623508ad0a65354c1` (GPL-3.0). Voyager was reviewed at
`6666432ec94e58089635dfe8ea3cc6967b59f12f`; its Talking Colonists integration is currently routed
through Colonist Errands. No public Colony Meetings source repository was found during this audit.

This is a migration disposition for the reviewed source revision, not a claim that an existing
Colonist Errands release already works against the new API.

## Talking Colonists mixins

| Errands mixin | Why it exists in the reviewed addon | Talking Colonists disposition |
| --- | --- | --- |
| `GeminiFlashMixin` | Memory JSON can arrive inside a Markdown fence and fail Gson parsing. | **Core fix.** Both memory generators use one strict `MemoryResponseParser` accepting plain JSON or one enclosing `json` fence. Invalid output is rejected before persistent mutation. No Flash-wide response mixin is needed. |
| `CitizenPromptServiceMixin` | Appends aliases, promises, truth blocks, research/death/build/Voyager context. | **Public extension.** Register composable `CitizenPromptContributor`s. Current observations and recollections are typed and bounded; contributors coexist instead of replacing the provider. |
| `GeminiWsClientMixin` | Prevent cut-off goodbye, suppress repeated goodbye, discard stale audio after token invalidation, learn rejected voices, stop endless reconnects. | **Core fixes.** Graceful close waits for playback; late post-final-turn output is discarded; invalidated-session queued audio is dropped; explicit voice rejection gets bounded model-scoped fallback; recovery has total-attempt/time bounds and idle non-player close 1008 is terminal. No client/stream mixin is intended. |
| `CitizenNeedAssessorMixin` | Suppresses urgent contact for addon military duty/promises and state the addon knows is already being handled. | **Public extension.** `CitizenConversationRules.registerUrgencyModifier` receives core's calculated weight; addon rules can reduce/zero it without reflecting config or copying core formulas. Current factual gaps should be contributed as observations until/unless core can derive them itself. |
| `PregenerationPlaybackMixin` | Captures the pregenerated `GeminiStream` so it can be interrupted. | **Core fix.** `PregenerationPlayback` owns active playback entries and higher-priority takeover cleanup. Addons should not access streams. |
| `PregenerationTaskServiceMixin` | Prevents cached morning/evening greetings from becoming stale. | **Core fix + public extension.** Non-threat cached prompts are time/weather/meal neutral by default. Additional addon constraints use `PregenerationPromptService`. |
| `McTalkingVoicechatPluginMixin` | Uses player voice activity to barge into pregenerated clips. | **Core fix.** Voice activity now reaches pregenerated playback even when no direct Live conversation exists, with debounce/early-playback protection. |
| `CitizenConversationMixin` | Marks small talk priority, flushes Flash/TTS tail, follows moving speakers. | **Core fixes / API.** Flash/TTS tail flushing and playback drain are owned by core; low-priority sessions no longer evict active sessions; the mixed locational channel follows the participants' centroid during playback. Addons use pair/controlled conversation handles rather than state/stream shadows. |
| `RandomConversationHandlerMixin` | Prevents workers/guards/addon-busy citizens from being selected for gossip. | **Public extension.** Register a `CitizenSpeechPolicy` and veto `ConversationKind.RANDOM_CITIZEN` (or other automatic kinds) from addon state. |
| `ConversationManagerMixin` | Makes low-priority capacity honest and avoids evicting someone mid-sentence. | **Core fix.** Low-priority capacity now means genuinely free capacity and low-priority claims never evict an existing conversation. Player conversations retain preemption priority. |

## Mixins that are not Talking Colonists integration points

`BlockHutTavernMixin` changes MineColonies' one-tavern placement rule and
`ItemAssistantHammerMixin` observes a MineColonies assistant-hammer action. Talking Colonists cannot
remove those mixins by exposing its own API; they target a different mod. If MineColonies exposes an
event/API for those behaviours, Colonist Errands should migrate to that upstream seam.

## Non-mixin internal access

| Reviewed Errands access | Supported replacement |
| --- | --- |
| Reflection into `AITools.playerConversationOnlyTools` | `AiToolRegistry.register(namespace, name, AiTool)` |
| `PlayerFunctionAction` / model-supplied actor lookup | `AiToolScope.PLAYER_CONVERSATION` + authoritative `AiToolContext.player()` |
| Repeated `ConversationManager.getPlayerForEntity` for tool authorization | Use `AiToolContext.player()` inside tools; otherwise `CitizenConversationService.activePlayerId(citizen)` |
| `CitizenDataMemoryExtended` casts | `CitizenMemoryService.addEvent`, `addFact`, `snapshot` |
| `markBusy` / `markNotBusy` for an errand | `CitizenConversationService.reserveActivity(...)` and close its `CitizenActivityReservation` |
| `getClientForEntity(...).endConversationWhenPossible()` | `CitizenConversationService.requestGracefulEnd(citizen)` |
| Direct `new CitizenConversation(...)` | `createPairConversation(...)` or `createControlledSession(...)` |
| `forceRemoveCooldown` | `CitizenConversationService.resetAutomaticCooldown(citizen)` |
| `hasLowPriorityCapacity` plus max-agent config reflection | `CitizenConversationService.hasAmbientCapacity(slots)` |
| `ConversationManager.hasPlayerNearby` for audible group-chat gating | `CitizenConversationService.hasPlayerNearby(citizen, range)` |
| Reflection into `CitizenConversation.state/stream` | Pair-handle state or controlled-turn completion future; stream remains private |
| Reflection into `CitizenWsClient.onSystemConversationEnded` | Controlled/ambient completion fires after audible playback |
| Reflection into `GeminiStream` queues/player | No replacement by design: queue drain, stale-output discard, cancellation and barge-in are core responsibilities |
| Reflection into `ConversationManager` slot/busy/background maps | No replacement by design: use availability/reservation/conversation services; lifecycle bookkeeping stays private and token-owned |
| Errands `SessionReaper` cleanup of pregeneration/compaction slots, orphan foreground slots, urgent walks and stale busy marks | **Core fix.** Background reservations are ownership-token scoped and deadline-bounded (pregen 3 min, compaction 10 min); orphan foreground claims are reaped after 2 min when no client/player owns them; urgent walks abort after 60 s; Flash/TTS and cached-playback busy state use bounded token-owned core activity reservations. Addons should not inspect or reap these maps. |
| Reflection into `UrgentContactHandler.walkingCitizens` | No replacement by design: core bounds walk lifecycle and repathing; addon policy can veto urgency/speech rather than reaping core state |
| Reflection into `LiveConversationWsClient.heldAudioChunks` | No replacement by design: playback ordering/drain is core-owned |
| Reflection into `McTalkingConfig.blockingTaskUrgencyMultiplier` | Urgency modifier receives the already-calculated core weight |

Other reflection in the reviewed addon targets MineColonies, optional economy/marketplace mods, or its
own compatibility layers and is outside Talking Colonists' API responsibility.

## Behaviour fixes incorporated into core

The reviewed workarounds also identified reliability issues that should not be extension points:

- Flash/TTS audio is flushed and drained before a normal conversation finishes.
- Low-priority background speech cannot cut off another ambient speaker to obtain capacity.
- A requested final turn drains audibly before graceful close and has a bounded fallback timeout.
- Output generated after that final turn is ignored, avoiding a second tool-driven goodbye.
- Replaying after an invalid session token discards stale queued audio first.
- Non-player sessions cannot reconnect indefinitely: recovery has a total attempt/time budget, and a
  silent provider-aborted `1008` session becomes terminal.
- Explicit unsupported-voice `1007` errors can select a stable fallback without treating unrelated
  websocket errors as voice failures.
- Pregenerated clips can be interrupted by deliberate nearby player speech and by higher-priority
  conversation takeover.
- Cached greetings are authored to remain valid when played later.
- Flash/TTS citizen-to-citizen audio follows the moving conversation group instead of remaining at
  its generation-time coordinate.
- Memory generation validates the complete response before mutation and coordinates generation vs
  save authorization exactly once.
- Background Live work has core-owned deadlines and ownership tokens, so an evicted task's delayed
  callback cannot close a replacement task for the same citizen.
- Orphan foreground reservations, hung internal busy activities, and urgent walk-to-player attempts
  have bounded core cleanup rather than requiring an addon watchdog.
- Delivery pregeneration failure/eviction clears its in-flight marker so the delivery can retry.

## Colony Meetings mapping

No implementation repository was available to inspect. The proposed ownership split maps directly to
`ControlledConversationSession`:

- **Meetings addon:** attendee selection, podium block, seats, navigation, hand raising, arrival,
  floor decisions, meeting start/end.
- **Talking Colonists:** one current speaker, prompt/dialogue context, Gemini capacity, audible
  playback completion, interruption, transcript attribution, cleanup.

A meeting should move the selected citizen first, then call `requestTurn`. Advance the floor only
when the returned future reports `COMPLETED`; this represents audible playback completion. Player
statements can be added to shared context with `addPlayerStatement`. Opening a meeting does not open
one provider connection per silent attendee.

## Compatibility note

The reviewed Colonist Errands source declares Talking Colonists `[1.7,1.8)`. It still imports and
mixes into 1.7-era internals. It must be migrated before claiming compatibility with the API described
here. The purpose of this work is to make that migration possible without replacing those hooks with
new internal dependencies.
