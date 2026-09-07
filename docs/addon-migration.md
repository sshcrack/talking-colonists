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
snapshot data includes relationships, broadcasts, and rumors.

```java
CitizenMemoryService.addFact(citizen, "The player promised to repair the bakery.");
CitizenMemoryService.addRelationshipChange(
        citizen,
        player.getUUID(),
        CitizenRelationshipDimension.TRUST,
        0.15,
        "The player kept a promise");
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
4. Advance the floor after the returned `AmbientLineResult` reports completion.
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
