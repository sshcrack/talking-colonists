# Addon API migration guide

This guide is for addon developers migrating integrations to Talking Colonists API generation 2.
The current API reference is [addon-api.md](addon-api.md); this document focuses on breaking changes,
legacy integration patterns, and their supported replacements.

API generation 2 deliberately makes `me.sshcrack.mc_talking.api` the addon boundary. Code under
`ConversationManager`, websocket/provider clients, audio queues, `duck` interfaces, mixins, handlers,
pregeneration caches, and other implementation packages must be treated as internal.

## Dependency setup

Use the developer-only API artifact for compilation and the normal Talking Colonists mod for the dev
runtime. See [addon-api.md](addon-api.md#runtime-vs-developer-artifact) for Maven coordinates and the
public repository.

Do not package `mc_talking-api` into the addon JAR and do not ask players to install it separately.

## General migration rule

When an addon previously reached into a Talking Colonists implementation class, migrate to the
semantic operation, snapshot, registration, event, or reservation exposed by the API. Do not replace
one internal dependency with another internal dependency.

If the API does not expose a concept an addon genuinely needs, request a supported API seam rather
than depending on manager collections, provider clients, queues, or mixin-only state.

## Common integration replacements

| Legacy integration pattern | API generation 2 replacement |
| --- | --- |
| Imports, reflection, accessors, or mixins targeting `ConversationManager` | `CitizenConversationService`, `CitizenConversationRules`, conversation handles, lifecycle listeners |
| Flat/constructible `CitizenPromptView` field access | Grouped read-only `CitizenPromptView`: `identity()`, `family()`, `wellbeing()`, `work()`, `colony()`, `conversation()`, `activity()`, `memories()` |
| Global prompt-provider mutation | `CitizenPromptService.registerContributor(...)` for composable context, or `registerProvider(...)` for complete providers |
| Prompt mixins that append addon facts | `CitizenPromptContributor` + typed `PromptContribution` |
| Direct MineColonies AI enums or raw state-name strings in addon contracts | Talking Colonists compatibility enums such as `CitizenAIState`, `AIWorkerState`, `MinimalAISubState`, `CitizenStatusType`, `HappinessModifierType`, `CitizenSkill` |
| Reflection into built-in AI tool maps | `AiToolRegistry.register(...)` |
| `FunctionAction` / player-only internal tool subclasses | `AiTool`, `AiToolScope`, and authoritative `AiToolContext.player()` / `requirePlayer()` |
| Provider-specific tool schema classes | `AiToolParameter` |
| World mutation directly from an AI callback thread | `AiToolContext.runOnServerThread(...)` or `supplyOnServerThread(...)` |
| Boolean-only conversation start handling | `ConversationStartResult`, `ConversationEligibility`, and `AmbientLineResult` |
| Polling manager/client state to learn when conversations start or end | `CitizenConversationService.registerLifecycleListener(...)` |
| Manual busy maps / `markBusy` / `markNotBusy` | `CitizenConversationService.reserveActivity(...)` and `CitizenActivityReservation` |
| Direct `new CitizenConversation(...)` | `createPairConversation(...)` or `createControlledSession(...)` |
| Meetings mixins that manipulate Gemini/audio/session state | `ControlledConversationSession`, typed `ControlledTurnResult`, `ControlledAudioAnchor`, and `ControlledConversationOptions` |
| Direct client shutdown / `endConversationWhenPossible()` | `CitizenConversationService.requestGracefulEnd(...)` |
| Internal cooldown mutation | `CitizenConversationService.resetAutomaticCooldown(...)` |
| Internal foreground/ambient slot inspection | `CitizenConversationService.hasAmbientCapacity(...)` and typed eligibility/start results |
| Internal player-conversation lookup | `AiToolContext.player()` inside tools or `CitizenConversationService.activePlayerId(...)` elsewhere |
| Direct memory duck-interface casts | `CitizenMemoryService` and `CitizenMemorySnapshot` |
| Pregeneration service/cache prompt mixins | `PregenerationPromptService.registerModifier(...)` |
| Raw audio queues, provider websocket streams, stale-token cleanup, playback drain/reaping | No addon API replacement; these are core-owned lifecycle responsibilities |

## Prompt-context migration

API generation 1 exposed a wide prompt snapshot. API generation 2 groups context by domain.

Typical field migrations look like this:

```java
// API generation 1 style
view.name();
view.jobName();
view.workAiState();
view.happiness();

// API generation 2
view.identity().name();
view.work().jobName();
view.activity().workState();
view.wellbeing().happiness();
```

Addon-owned facts should generally be supplied with a contributor:

```java
AddonRegistration registration = CitizenPromptService.registerContributor(
        "my_addon:expedition",
        100,
        context -> List.of(PromptContribution.observation(
                "my_addon:expedition",
                "Expedition state",
                context.view().identity().name() + " returned from the expedition.")));
```

Use a full `CitizenPromptProvider` only when the addon intentionally owns complete prompt generation.

## MineColonies state migration

Talking Colonists-owned compatibility enums are intentionally separate from MineColonies enums.
This keeps addon binaries insulated from MineColonies patch releases that change enum classes or
constants.

Addon code should switch on the Talking Colonists types:

```java
switch (view.activity().workState()) {
    case FARMER_HARVEST -> onHarvesting();
    case NEEDS_ITEM -> onWaitingForSupplies();
    case UNKNOWN -> onUnknownWorkState();
    default -> { }
}
```

`UNKNOWN` is the runtime compatibility fallback. Talking Colonists' own compatibility tests require
known states in each supported MineColonies dependency to map to explicit API values, so MineColonies
updates are handled in Talking Colonists instead of every addon.

The same rule applies to citizen AI state, fine-grained activity state, visible status, happiness
modifier type, and citizen skill.

## AI tool migration

Internal `FunctionAction` subclasses should become either `AiQueryTool` or `AiCommandTool`
registrations. Do not copy `AITools` maps, reflect into `GeminiWsClient`, or preserve a parallel
legacy registration path in the addon.

A world-changing/player-authorized action is a command:

```java
AddonRegistration registration = AiToolRegistry.register("my_addon", "accept_job", new AiCommandTool() {
    @Override
    public String description() {
        return "Accept the offered addon job.";
    }

    @Override
    public AiToolScope scope() {
        return AiToolScope.PLAYER_CONVERSATION;
    }

    @Override
    public AiToolPermission permission() {
        return AiToolPermission.MANAGE_HUTS;
    }

    @Override
    public AiToolParameter parameters() {
        return AiToolParameter.object(Map.of(
                "job", AiToolParameter.string(true)
        ));
    }

    @Override
    public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
        // Core invokes this method on the server thread and rechecks current colony permission.
        ServerPlayer player = context.requirePlayer();
        acceptJob(context.citizen(), player, parameters.get("job").getAsString());
        JsonObject result = new JsonObject();
        result.addProperty("accepted", true);
        return CompletableFuture.completedFuture(result);
    }
});
```

Use `AiQueryTool` instead when the operation is a short read-only lookup. Both contracts receive the
same authoritative `AiToolContext`. Do not trust player UUID/name, colony rank, or session identity
from model JSON; those values are not authority. Core validates the parameter schema and resolves
the actual actor from the owning conversation.

Do not emulate command retry handling in the addon. Core reserves the provider function-call ID,
returns an operation ID, suppresses duplicate side effects, bounds retained results, routes delayed
completion only to the still-active owning session, and invokes `AiCommandTool.onCompletion` even
when delivery is no longer possible. Later asynchronous world mutations must be marshalled with
`runOnServerThread`/`supplyOnServerThread`.

## Conversation migration

For availability, use typed eligibility:

```java
ConversationEligibility eligibility =
        CitizenConversationService.eligibility(citizen, ConversationKind.ADDON_AMBIENT);
```

For a player-started conversation:

```java
ConversationStartResult result =
        CitizenConversationService.startPlayerConversation(player, citizen);
```

For one-sided addon speech that must finish audibly before gameplay continues:

```java
CitizenConversationService
        .requestAmbientLine(citizen, "Thank the player for completing the task.")
        .thenAccept(result -> {
            if (result.completed()) {
                continueTask();
            }
        });
```

Use `registerLifecycleListener(...)` when the addon needs start/end observation instead of polling
internal manager or client state.

## Activity/busy-state migration

Addon activities that should temporarily exclude automatic speech must use a reservation:

```java
Optional<CitizenActivityReservation> reservation =
        CitizenConversationService.reserveActivity(citizen, "my_addon:delivery");

reservation.ifPresent(handle -> {
    try (handle) {
        runDelivery();
    }
});
```

Reservations have bounded leases. Long activities can call `renew(...)`. Closing or expiration is
ownership-token safe, so a stale handle cannot clear a replacement reservation.

Do not maintain a shadow copy of Talking Colonists busy/foreground/background maps and do not build
an addon watchdog that reaps core-owned sessions.

## Memory migration

Use `CitizenMemoryService` for facts, events, relationship changes, and immutable snapshots. Structured
snapshot data includes relationships, broadcasts, and rumors. Low-level `addFact`/`addEvent` writes
are intentionally unattributed addon-direct memories; do not use them to assert a player's promise or
other authenticated player statement. Record player speech through the controlled-session transcript,
and persist gameplay facts only after the addon has authoritatively observed the outcome.

```java
CitizenMemoryService.confirmOutcome(citizen, new AddonConfirmedOutcome(
        "my_addon",
        "bakery_repair_" + repairJobId,
        "The player completed the bakery repair.",
        player.getUUID(),
        List.of("The bakery was repaired as agreed."),
        List.of(new ConfirmedRelationshipChange(
                player.getUUID(),
                CitizenRelationshipDimension.TRUST,
                0.15f))
));
```

Prompt text assembled from memory is presentation logic and should not be parsed as a data format.

## Pregeneration migration

Addon constraints for pregenerated speech belong in a registered modifier:

```java
AddonRegistration registration = PregenerationPromptService.registerModifier(
        "my_addon:delivery_context",
        100,
        (context, prompt) -> prompt + "\nMention the completed delivery if relevant.");
```

Playback streams, provider reconnect state, queued audio, interruption cleanup, and cached-session
ownership stay inside Talking Colonists.

## Meetings and multi-citizen flows

Use `ControlledConversationSession` when an addon controls agenda, floor order, seating, navigation,
or other gameplay while Talking Colonists controls AI turns and audible playback.

A typical meeting addon should:

1. Select/move attendees using its own gameplay logic.
2. Create a controlled session with the participants and agenda.
3. Call `requestTurn(...)` for the selected speaker.
4. Advance the floor after the returned `ControlledTurnResult` reports completion.
5. Add authenticated player statements with `addPlayerStatement(...)` when needed.
6. Read `transcript()` for structured speaker attribution.
7. Close/end the session when the meeting finishes.

Use `createPairConversation(...)` for ordinary two-citizen autonomous conversation flow.

## Migration checklist

Before declaring an addon API-generation-2 compatible:

- Compile against `me.sshcrack:mc_talking-api`, not the full implementation JAR as an API surface.
- Remove imports/reflection/mixins targeting Talking Colonists implementation packages.
- Replace raw/MineColonies closed-state contracts with Talking Colonists compatibility enums.
- Store every returned `AddonRegistration` for the lifetime of the registration.
- Use typed conversation results instead of inferring failure from manager state.
- Use activity reservations instead of shadow busy bookkeeping.
- Keep provider/audio/session internals out of addon code.
- Test against every Minecraft/loader artifact the addon declares compatible.


## Pinned Colonist Errands audit

For migration evidence, Talking Colonists reviewed `Lovkar-Squid/colonist-errands` at commit
`f270362aca847726087213c623508ad0a65354c1`. The checked-out source carries the GNU GPL v3 license.
This audit records integration patterns only; no Colonist Errands source code was copied into Talking
Colonists as part of roadmap item 12, so no third-party source notice is being incorporated into the
Talking Colonists distribution. Voyager was reviewed at
`6666432ec94e58089635dfe8ea3cc6967b59f12f`.

This is a disposition for that pinned source revision, not a compatibility claim for an existing
Colonist Errands release.

### Talking Colonists-targeting mixins

| Reviewed mixin | Purpose in pinned Errands source | Migration disposition |
| --- | --- | --- |
| `GeminiFlashMixin` | Accept fenced memory JSON before Gson parsing. | **Core fix.** Structured memory parsing accepts the supported fenced/plain forms before mutation; no provider-wide mixin is needed. |
| `CitizenPromptServiceMixin` | Append aliases, promises, truth blocks, research/death/build/Voyager context. | **Public extension.** Use `CitizenPromptContributor` and typed `PromptContribution`s. |
| `GeminiWsClientMixin` | Work around goodbye truncation/repetition, stale audio, rejected voices, and runaway reconnect. | **Core fix.** Audible graceful close, stale-output rejection, bounded model-scoped voice fallback, and bounded provider recovery are core-owned. |
| `CitizenNeedAssessorMixin` | Suppress urgent contact for addon military/promise state. | **Public extension.** Use `CitizenConversationRules.registerUrgencyModifier(...)` and speech policy as appropriate. |
| `PregenerationPlaybackMixin` | Capture raw pregenerated stream for interruption. | **Core fix.** Pregenerated playback interruption/takeover is core-owned; raw streams stay internal. |
| `PregenerationTaskServiceMixin` | Avoid stale cached greeting context. | **Core fix + public extension.** Core keeps reusable prompts time-neutral; addon constraints use `PregenerationPromptService`. |
| `McTalkingVoicechatPluginMixin` | Use nearby player speech to interrupt pregenerated playback. | **Core fix.** Core routes voice activity to pregenerated playback interruption. |
| `CitizenConversationMixin` | Priority, Flash/TTS drain, moving audio origin, conversation state/stream access. | **Core fix + public API.** Core owns drain/following; addons use pair or controlled conversation handles. |
| `RandomConversationHandlerMixin` | Exclude workers/guards/addon-busy citizens from gossip. | **Public extension.** Use a `CitizenSpeechPolicy` for the relevant `ConversationKind`. |
| `ConversationManagerMixin` | Keep low-priority capacity honest and avoid evicting active speech. | **Core fix.** Ambient capacity/ownership is core-tokenized; low priority never evicts active foreground work. |

`BlockHutTavernMixin` and `ItemAssistantHammerMixin` target MineColonies, not Talking Colonists. They
therefore have **no Talking Colonists API replacement**; they need a MineColonies-side event/API if
the addon wants to remove those mixins.

### Non-mixin internal hooks

| Reviewed internal hook or reflection pattern | Supported disposition |
| --- | --- |
| Reflection into `AITools.playerConversationOnlyTools` | `AiToolRegistry.register(...)` |
| Internal `FunctionAction`/player action types and model-supplied actor lookup | `AiQueryTool`/`AiCommandTool`, `AiToolScope`, and authoritative `AiToolContext` |
| `ConversationManager.getPlayerForEntity(...)` for tool authority | `AiToolContext.player()/requirePlayer()` inside tools; `CitizenConversationService.activePlayerId(...)` otherwise |
| `ConversationManager.canCitizenSpeak(...)`, `isPlayerInConversation(...)`, and `startPlayerConversation(...)` precheck/start flow | Use typed `eligibility(...)` / `ConversationStartResult`; do not split a start into racy manager prechecks |
| `CitizenDataMemoryExtended` casts for facts/events/relationships | `CitizenMemoryService`; use `confirmOutcome(...)` for addon-confirmed gameplay outcomes |
| `ConversationManager.markBusy/markNotBusy/isCitizenBusy` shadow ownership | `reserveActivity(...)` plus conversation eligibility/handles; do not mirror manager state |
| Direct `getClientForEntity(...).endConversationWhenPossible()` / client shutdown | `requestGracefulEnd(...)` |
| `ConversationManager.endConversation(...)` | Use the owning conversation/session handle or `requestGracefulEnd(...)`; forced internal teardown is not an addon operation |
| Direct `new CitizenConversation(...)` | `createPairConversation(...)` or `createControlledSession(...)` |
| `forceRemoveCooldown(...)` | `resetAutomaticCooldown(...)` |
| `hasLowPriorityCapacity(...)` plus config reflection | `hasAmbientCapacity(...)` |
| `hasPlayerNearby(...)` | `CitizenConversationService.hasPlayerNearby(...)` |
| Reflection into `CitizenConversation.state/stream` | Pair handle state or controlled-turn future; provider/audio stream stays private |
| Reflection into `CitizenWsClient.onSystemConversationEnded` | Controlled/ambient terminal completion after audible playback |
| Reflection into `GeminiStream` queues/player and `LiveConversationWsClient.heldAudioChunks` | **No raw replacement by design.** Queue drain, stale output, cancellation, and barge-in are core-owned. |
| Reflection into `ConversationManager` foreground/background/client/busy maps | **No raw replacement by design.** Use service operations, lifecycle events, and reservations. |
| `ConversationManager.getClients()`, `isUrgentConversation(...)`, `recordCooldown(...)`, `releaseSlot(...)`, `releaseBackgroundSlot(...)`, and `unregisterExternalClient(...)` | **No raw replacement by design.** These are core lifecycle/ownership bookkeeping; use lifecycle events, typed starts/results, reservations, and session handles instead. |
| Errands `SessionReaper` access to background slots, orphan foreground slots, urgent walks, and busy maps | **Core fix.** Bounded ownership-token cleanup and shutdown handling remove the need for an addon reaper. |
| Reflection into `UrgentContactHandler.walkingCitizens` | **No raw replacement by design.** Urgent walking lifecycle is bounded by core; addons influence policy, not internal bookkeeping. |
| Reflection into `McTalkingConfig.blockingTaskUrgencyMultiplier` | Urgency modifier receives core's already-computed weight |
| Direct `PregenerationTaskService.hasPlayerGreeting(...)` cache inspection | **No cache API.** Addons should request/modify semantic pregeneration behavior, not coordinate against cache internals. |
| `GreetingCheck`, `AudioGate`, `StreamDrain`, `ChatWindDown`, `C2cAudioFollower`, and `SlotGuard` helpers built around raw stream/client state | **Core fix / no raw replacement.** Their Talking Colonists-facing responsibilities are covered by playback drain/interruption, pair/controlled session handles, and bounded ownership cleanup. |

Other reflection found in the pinned repository targets MineColonies, optional economy/marketplace
mods, or Colonist Errands' own compatibility layers and is outside the Talking Colonists API boundary.
