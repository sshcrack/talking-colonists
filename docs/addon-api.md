# Addon API

Talking Colonists treats addons as a first-class integration surface. Supported addon code lives
under `me.sshcrack.mc_talking.api`; `ConversationManager`, websocket clients, audio queues, handlers,
`duck` interfaces, pregeneration caches and other implementation packages are not API.

This document describes the supported **API generation 2** addon contract.
`TalkingColonistsApi.API_MAJOR_VERSION` is `2`. Migration guidance is maintained separately in
[addon-migration.md](addon-migration.md).

## Runtime vs developer artifact

**Players and servers install only the normal Talking Colonists mod.** There is no second API mod to
install.

Addon developers should use the matching developer artifact as a compile/IDE dependency. Published
API artifacts are available from the public sshcrack Maven repository, browsable at
<https://maven.sshcrack.me/#/>. Gradle should use the repository endpoint
<https://maven.sshcrack.me/releases>:

```kotlin
repositories {
    maven("https://maven.sshcrack.me/releases")
}

dependencies {
    compileOnly("me.sshcrack:mc_talking-api:<talking-colonists-version>-<minecraft-version>-<loader>")

    // Your dev runtime should contain the normal Talking Colonists mod.
    // Do not package mc_talking-api into your addon jar.
}
```

For example, API generation 2 for Talking Colonists `1.7.1`, Minecraft `1.21.1`, NeoForge uses:

```kotlin
compileOnly("me.sshcrack:mc_talking-api:1.7.1-1.21.1-neoforge")
```

The API artifact uses the same version tuple as the normal published mod artifact. Always choose the
artifact matching the Talking Colonists version, Minecraft version, and loader your addon targets.

The supported sources live in `src/api/java` and are compiled as a separate `addonApi` source set
that cannot see Talking Colonists `main` output. The developer JAR contains no Forge/NeoForge mod
metadata, mixin metadata, provider implementation classes or Talking Colonists internals. The normal
Talking Colonists mod embeds the same public API classes plus the runtime implementation.

Compile examples in
`src/apiTest/java/me/sshcrack/mc_talking/api/examples/AddonApiCompileExample.java` are compiled
against the actual stripped developer JAR. This catches accidental implementation dependencies that
would otherwise be hidden by the full mod classpath.

Minecraft, MineColonies and Gson types are intentionally used where addons operate on those same
objects. Provider/transport implementation types such as Gemini Live Lib are not part of the addon
contract.

## Registration lifetime

Composable extension points return `AddonRegistration`:

```java
AddonRegistration registration = ...;
registration.close();
```

A handle closes exactly the registration that created it. A stale handle cannot unregister a newer
registration with the same conceptual purpose. Registries validate namespaced IDs and use stable
ordering (`order`, then ID) so multiple addons can coexist deterministically.

Keep process/mod-lifetime registrations open for as long as the addon is loaded. Do not close them
on a server stop unless the addon will register them again for the next server.

## Normalized citizen context

`CitizenPromptView` is a read-only grouped interface. Talking Colonists-owned compatibility enums form
a stable firewall between addon code and MineColonies patch-level API churn.

```java
CitizenPromptView snapshot = CitizenContextService.snapshot(citizen, player);

String name = snapshot.identity().name();
String job = snapshot.work().jobName();
CitizenActivityCategory category = snapshot.activity().category();
AIWorkerState exactWorkState = snapshot.activity().workState();
String activityText = snapshot.activity().description();
```

The groups are:

- `identity()` — name, age/sex flags, guard flag and personality.
- `family()` — parents, partner, children and siblings.
- `wellbeing()` — health, sickness, hunger, happiness, blockers and food situation.
- `work()` — job, home/workplace, skills, requests and quests.
- `colony()` — colony identity, world/raid context, history, diplomacy and recent events.
- `conversation()` — response language and optional authenticated speaking-player context.
- `activity()` — stable semantic category, typed compatibility states/sub-state, normalized description and recent actions.
- `verifiedFacts()` — point-in-time health, citizen inventory/equipment, housing, request state and builder activity with explicit freshness/availability.
- `memories()` — immutable provenance-aware Talking Colonists memory snapshot when present.

Talking Colonists owns semantic categories such as `WORKING`, `EATING` and `MOURNING`. Exact state
concepts needed by addons are exposed through Talking Colonists-owned compatibility enums
(`CitizenAIState`, `AIWorkerState`, `MinimalAISubState`). Addons therefore get autocomplete, exhaustive
switches and typo safety without linking their API contract to MineColonies enum classes. Unknown/new
MineColonies values map to `UNKNOWN` at runtime; Talking Colonists' compatibility tests deliberately fail
when a newly selected MineColonies version contains an unmapped known value, so only Talking Colonists
needs updating. `CitizenStatusType`, `HappinessModifierType`, and `CitizenSkill` follow the same pattern.

### Verified fact freshness

`verifiedFacts()` separates values from observation state. `CURRENT` with a zero/empty value is a real
observation; `UNLOADED` means the authoritative entity/chunk needed for that field is not loaded;
`UNAVAILABLE` means no usable backing value was exposed; `STALE` is reserved for retained observations
that are no longer current. Do not turn an unavailable observation into `0`, `false`, "empty", or an
opposite state.

```java
var facts = snapshot.verifiedFacts();
if (facts.healthPercent().state() == ObservationState.CURRENT) {
    double hp = facts.healthPercent().value();
}

switch (facts.housingStatus()) {
    case HOUSED -> { /* assigned residence */ }
    case GUARD_QUARTERS -> { /* guard workplace is quarters */ }
    case HOMELESS -> { /* verified no home assignment */ }
    case UNKNOWN -> { /* do not infer either way */ }
}
```

A `CitizenPromptView` is a point-in-time server-thread snapshot, not a live subscription. The core
prompt path deliberately avoids colony-wide warehouse scans. When gameplay is about to act on a
possibly changed fact, take a fresh `CitizenContextService.snapshot(...)` or use a current query/tool
rather than relying on a prompt or recollection assembled earlier in the conversation. Exact colony
stock counts belong in an explicit server-thread query where that cost is intentional.

## Prompt extensions

Most addons should contribute bounded context through prompt contributors. The callback receives one
immutable `PromptContributionContext` containing the citizen snapshot, the prompt surface, and
per-session context:

```java
var expedition = CitizenPromptService.registerContributor(
        "my_addon:expedition",
        100,
        context -> List.of(PromptContribution.observation(
                "my_addon:expedition_state",
                "Expedition state",
                context.view().identity().name() + " returned with two chorus flowers.")));

var meetingAgenda = CitizenPromptService.registerContributor(
        "my_addon:meeting_agenda",
        110,
        context -> context.session().agenda() == null
                ? List.of()
                : List.of(PromptContribution.instruction(
                        "my_addon:meeting_agenda",
                        "Meeting agenda",
                        "Keep this turn relevant to: " + context.session().agenda())));
```

`context.view()` is a server-thread snapshot assembled before provider/background generation begins.
Its nested lists/maps are immutable copies. Contributor callbacks may run on provider/background
threads, so **do not read or mutate Minecraft/MineColonies world state from a contributor**; capture
addon state into your own immutable data before it is needed or expose it through a core-supported
snapshot/service.

`context.target()` identifies the exact prompt surface (`CITIZEN_ROLEPLAY`,
`SYSTEM_CONTROLLED_ROLEPLAY`, `CONVERSATIONAL_INFO`, `BASIC_CITIZEN_INFO`, or
`DETAILED_CITIZEN_INFO`). Each surface invokes a contributor at most once per assembled prompt.
Pregenerated Live speech uses `SYSTEM_CONTROLLED_ROLEPLAY`; Flash/TTS multi-citizen generation uses
`CONVERSATIONAL_INFO`.

`context.session()` is a fresh immutable value for the current session/turn. Controlled sessions
expose their current agenda there; `setAgenda(...)` affects subsequent turns, never a prompt already
being assembled. Session data is not stored in the global contributor registry, so one meeting cannot
leak agenda/context into another server/session.

Contributors run in ascending `order`, then namespaced registration ID. Duplicate IDs are rejected.
A failing contributor is logged and skipped without disabling core or other addons. Each contribution
and the total rendered addon block budget are bounded. `source` is required and rendered with the block so provenance is explicit. Section/source labels
must be single-line values.

Use:

- `observation` for current server/addon-verified facts.
- `recollection` for remembered/inferred facts that may be stale.
- `instruction` for addon-owned conversational guidance, never as permission or authority.

These categories render as separate prompt sections. Talking Colonists appends a core-priority
boundary after addon context: addon guidance cannot override core safety, permissions, tool authority,
or roleplay rules, and current observations take precedence over contradictory recollections.

Registrations have process/mod lifetime until their `AddonRegistration` is closed. Keep normal mod
registrations alive across integrated/dedicated server restarts; put changing session state in
`PromptContributionContext.session()` rather than unregistering/re-registering global contributors.

An integration that deliberately owns the complete base prompt can register a provider:

```java
var registration = CitizenPromptService.registerProvider(
        "my_addon:provider",
        100,
        myProvider);
```

The highest provider priority wins; ties are deterministic by namespaced ID. Closing that handle
selects the next provider or the Talking Colonists default. API generation 2 is intentionally a clean
breaking baseline; legacy global-provider mutation methods are not retained as compatibility shims.

## AI tools

Register addon tools with `AiToolRegistry`. A tool must choose exactly one execution contract:
`AiQueryTool` for a short synchronous query or `AiCommandTool` for world-changing/delayed work.

A query is executed by core on the Minecraft server thread and returns a `completed` result:

```java
var query = AiToolRegistry.register("my_addon", "current_destination", new AiQueryTool() {
    @Override
    public String description() {
        return "Read the citizen's current addon destination.";
    }

    @Override
    public AiToolParameter parameters() {
        return AiToolParameter.object(Map.of());
    }

    @Override
    public JsonObject executeQuery(AiToolContext context, JsonObject parameters) {
        JsonObject result = new JsonObject();
        result.addProperty("citizen", context.citizen().getName().getString());
        result.addProperty("sessionId", context.sessionId().toString());
        return result;
    }
});
```

Commands are started on the Minecraft server thread, but return a completion stage immediately.
This lets core acknowledge the model with an operation ID while the addon finishes later:

```java
var command = AiToolRegistry.register("my_addon", "come_here", new AiCommandTool() {
    @Override
    public String description() {
        return "Ask this citizen to come to the authenticated player.";
    }

    @Override
    public AiToolScope scope() {
        return AiToolScope.PLAYER_CONVERSATION;
    }

    @Override
    public AiToolPermission permission() {
        return AiToolPermission.RIGHTCLICK_ENTITY;
    }

    @Override
    public AiToolParameter parameters() {
        return AiToolParameter.object(Map.of(
                "urgency", AiToolParameter.enumeration(List.of("normal", "urgent"), false)
        ));
    }

    @Override
    public CompletionStage<JsonObject> executeCommand(AiToolContext context, JsonObject parameters) {
        return beginAddonPlanningAsync().thenCompose(plan ->
                context.supplyOnServerThread(() -> {
                    ServerPlayer player = context.requirePlayer();
                    startNavigation(context.citizen(), player, plan);
                    JsonObject result = new JsonObject();
                    result.addProperty("started", true);
                    return result;
                }));
    }

    @Override
    public void onCompletion(AiToolOperationOutcome outcome) {
        // Runs even if the conversation ended before the command finished.
        // outcome.deliveredToSession() says whether core could still route it to the model.
    }
});
```

### Validation and authority

`AiToolParameter` is Talking Colonists-owned and provider-neutral. The root schema must be an
object. Core validates every call before execution: required fields must exist, values must match
the declared primitive/enum/array/object type, `null` is rejected, and undeclared fields are
rejected. Provider JSON is therefore data only; it cannot add an undeclared player, rank, session,
or other authority field.

`AiToolContext` is authoritative and contains a core-generated `sessionId()`, the acting citizen,
the citizen's server-side colony, and the authenticated initiating player when one exists. Never
use a player UUID, rank, or session ID from model JSON as authorization. `authenticatedPlayerId()`
and `requirePlayer()` derive from the conversation instead. NPC/system sessions have no implicit
player authority.

For common colony mutations, declare an `AiToolPermission`. Core maps that stable API value to the
current MineColonies permission and calls `colony.getPermissions().hasPermission(...)` at execution
time. A rank/permission change after the tool was advertised is therefore effective immediately.
`AiToolPermission.NONE` skips the colony-permission check; custom domain authorization can be added
with side-effect-free `canExecute(context)`. `PLAYER_CONVERSATION` still independently requires an
authenticated player.

### Threading and asynchronous outcomes

Authorization, queries, and the initial `executeCommand` call run on the Minecraft server thread.
Queries must return promptly. `executeCommand` must also return its `CompletionStage` promptly; do
not block it waiting for long work. Any later asynchronous continuation that mutates Minecraft must
use `context.runOnServerThread(...)` or `context.supplyOnServerThread(...)`. `onCompletion` may run
off the server thread.

Provider responses use a stable envelope:

```json
{ "status": "accepted",  "operationId": "...", "toolId": "my_addon:come_here" }
{ "status": "completed", "operationId": "...", "toolId": "my_addon:come_here", "result": {} }
{ "status": "failed",    "operationId": "...", "toolId": "my_addon:come_here",
  "error": { "code": "unauthorized", "message": "..." } }
{ "status": "cancelled", "operationId": "...", "toolId": "my_addon:come_here",
  "error": { "code": "cancelled", "message": "..." } }
```

A command uses the provider's function-call ID as its idempotency key within the owning session. A
retry with the same call ID and identical tool/arguments returns the existing operation and never
starts the side effect again. Reusing that call ID with different arguments fails with
`call_id_conflict`. Commands without a provider call ID fail rather than executing without
idempotency. Core bounds active commands and terminal-result retention; terminal records are also
dropped when their conversation intentionally closes. Provider call IDs are unique call tokens,
not addon persistence IDs.

When a delayed command finishes, core routes the terminal result back into the same conversation
only if that provider session is still active. It never queues the result through reconnect logic.
If the session is closed or temporarily unavailable, no connection is reopened; `onCompletion`
still fires with `deliveredToSession() == false`. Cancelling the returned future produces a
`cancelled` outcome.

The provider-facing function name derived from an addon ID is private implementation detail. Persist
only your namespaced addon/tool ID.

## Conversation eligibility and policy

Addon state can participate in speech and urgency policy through registered rules:

```java
var speech = CitizenConversationRules.registerSpeechPolicy(
        "my_addon:military_duty",
        100,
        (citizen, kind) -> kind == ConversationKind.PLAYER || !isOnMilitaryDuty(citizen));

var urgency = CitizenConversationRules.registerUrgencyModifier(
        "my_addon:handled_need",
        100,
        (citizen, currentWeight) -> isAlreadyHandled(citizen) ? 0.0 : currentWeight);
```

Speech policies are veto-only. They cannot bypass core sleeping, visitor, cooldown or busy rules.
Urgency modifiers receive the already-calculated core weight and therefore do not need to mirror
Talking Colonists formulas/configuration.

Use detailed eligibility when an addon needs a reason:

```java
ConversationEligibility eligibility =
        CitizenConversationService.eligibility(citizen, ConversationKind.ADDON_AMBIENT);

if (!eligibility.eligible()) {
    // eligibility.status() / eligibility.detail()
}
```

## Starting speech and conversations

Player conversation starts return a typed immediate result:

```java
ConversationStartResult result =
        CitizenConversationService.startPlayerConversation(player, citizen);

if (!result.started()) {
    // PROVIDER_UNAVAILABLE, IN_USE_BY_OTHER_PLAYER, CAPACITY_EXHAUSTED, ...
}
```

Addon-directed ambient speech returns a future that reaches terminal state after audible playback:

```java
CitizenConversationService
        .requestAmbientLine(citizen, "Thank the courier for the delivery in one sentence.")
        .thenAccept(result -> {
            if (result.completed()) {
                // Safe to continue gameplay after the spoken line actually finished.
            } else if (result.status() == AmbientLineResult.Status.REJECTED) {
                // result.rejectionReason() explains why it could not start.
            }
        });
```

`hasAmbientCapacity(slots)`, `hasPlayerNearby(citizen, range)`, `activePlayerId(citizen)`,
`activeKind(citizen)` and `requestGracefulEnd(citizen)` provide common conversation queries and actions.

## Conversation lifecycle observation

Observe core-managed audible conversations without a mixin:

```java
var registration = CitizenConversationService.registerLifecycleListener(
        "my_addon:conversation_ui",
        100,
        event -> {
            if (event.phase() == ConversationLifecycleEvent.Phase.STARTED) {
                // event.kind(), event.citizen(), event.playerId(), event.gameTimeTicks()
            }
        });
```

Listener failures are isolated. Core dispatches lifecycle events outside manager critical sections
and on the Minecraft server executor when one is available.

## Reserving citizens for addon gameplay

Use an activity lease when an addon task occupies a citizen without opening a Talking Colonists
conversation:

```java
Optional<CitizenActivityReservation> reservation =
        CitizenConversationService.reserveActivity(
                citizen,
                "my_addon:delivery",
                Duration.ofMinutes(15));
```

The convenience overload uses a 10-minute lease. Explicit leases must be positive and at most one
hour. Long-running tasks renew the exact lease:

```java
reservation.ifPresent(r -> r.renew(Duration.ofMinutes(15)));
```

Close the handle immediately when the task ends. Leases also expire automatically, so a crashed or
forgotten addon task cannot leave a citizen permanently busy. Ownership tokens ensure an expired or
stale handle cannot renew/release a replacement reservation.

A direct player conversation retains core takeover priority; addons never own provider slot maps.
If a player takes over a citizen while an addon activity lease is active, core invalidates that exact
lease before the player session starts. Calls made later through the stale addon handle cannot renew
or release the replacement owner.

## Core session ownership and recovery

Provider clients, foreground/background capacity, reconnect state, and cooldown bookkeeping are
core-owned lifecycle state. Addons observe conversations through lifecycle listeners and supported
conversation handles; they do not maintain a second busy map or reconnect watchdog.

Foreground sessions use opaque ownership tokens internally. Ambient work never evicts an active
foreground session. A direct player conversation may preempt non-player foreground work, but it
never evicts another direct player conversation. Delayed close callbacks from the preempted session
are ignored once ownership changes, so they cannot clear the replacement citizen/client state.

Gemini Live transport recovery is bounded and diagnostic. Transient transport/service disconnects
may reconnect within core's recovery budget, while authentication, configuration, provider-policy,
and quota failures terminate the owning session. Intentional local closure suppresses reconnect.
Terminal cleanup releases the owned foreground capacity exactly once; addons should react to the
lifecycle result instead of attempting provider recovery themselves.

## Memories

Citizen memory exposes provenance as well as compatibility `facts()` / `events()` lists. Persistent
entries identify participants by UUID; names may appear in human-readable prose but are not the
attribution key. `MemoryProvenance` distinguishes observed gameplay events, explicit player statements,
citizen statements, addon-confirmed outcomes, and migrated legacy data. Citizen-only speech is never
proof that a player spoke, promised, agreed, or completed an action.

For an addon-owned gameplay outcome, use `confirmOutcome`. The `(source, idempotencyId)` pair is a
durable idempotency key: retrying it does not duplicate event/fact memories and does not reapply any
relationship delta, including after save/reload or if the displayed memory text is later removed.

### Colonist Errands-style promise fulfillment

The addon should confirm the **fulfilled gameplay outcome**, not ask the citizen model to infer a player
promise from speech:

```java
String fulfillmentId = "promise:" + promise.id() + ":fulfilled";
var result = CitizenMemoryService.confirmOutcome(citizen, new AddonConfirmedOutcome(
        "colonist_errands:promises",
        fulfillmentId,
        "The bread delivery I was waiting for was completed.",
        player.getUUID(),
        List.of("The tracked bread promise is fulfilled."),
        List.of(new ConfirmedRelationshipChange(
                player.getUUID(),
                CitizenRelationshipDimension.TRUST,
                0.2f))
));

if (result == AddonMemoryWriteResult.DUPLICATE) {
    // Safe retry: no second memory and no second +0.2 trust change.
}
```

Promise rules, rewards, due dates and whether a delivery actually satisfies a promise remain Errands
policy. Talking Colonists stores the confirmed outcome and provenance; it does not own promise gameplay.

### Voyager-style expedition facts

An expedition addon can persist the return event and compact facts in the same idempotent transaction:

```java
CitizenMemoryService.confirmOutcome(voyager, new AddonConfirmedOutcome(
        "colonist_errands:voyager",
        expedition.id() + ":returned",
        "I returned safely from the End expedition.",
        voyager.getUUID(),
        List.of(
                "My last expedition destination was the End.",
                "The expedition brought back two chorus flowers."),
        List.of()
));
```

Read provenance through `snapshot.entries()` and relationship contribution provenance through
`snapshot.relationshipChanges()`:

```java
var memory = CitizenMemoryService.snapshot(citizen).orElseThrow();
for (CitizenMemoryEntryView entry : memory.entries()) {
    // type(), provenance(), participantId(), source(), idempotencyId(), content()
}
```

The older direct `addEvent`, `addFact`, and `addRelationshipChange` operations remain low-level memory
writes and do not establish addon-confirmed provenance. Use `confirmOutcome` whenever the addon is
asserting that a concrete gameplay outcome happened. Memory storage, compaction, session tokens,
broadcast/rumor propagation and save coordination remain core responsibilities.

## Autonomous citizen conversations

For a regular two-citizen conversation:

```java
var conversation = CitizenConversationService.createPairConversation(server, alice, bob);
conversation.setStateListener(state -> {
    // GENERATING / PLAYING_AUDIO / ENDED
});
conversation.start();
```

The handle hides provider clients, streams, audio queues, slot ownership and teardown.

## Controlled meetings and councils

For a complete three-citizen Colony Meetings-style flow, including caller-owned arrival gates, player questions, failure recovery, podium output routing, microphone limitations, and manual validation notes, see [Controlled meetings integration](meetings-integration.md).

The addon owns attendance, navigation, seats, hand raising and floor policy. Talking Colonists owns
provider capacity, prompt grounding, spatial playback and cancellation. Opening a controlled session
is cheap: silent attendees do **not** reserve a provider slot or open Live connections. Only the
current speaker claims foreground capacity for the duration of one turn. Controlled turns use their
own `ConversationKind.CONTROLLED`: deliberate meeting speech is not blocked by, and does not record,
the automatic ambient-conversation cooldown. Addon speech policies can still distinguish and veto
controlled turns explicitly.

```java
var meeting = CitizenConversationService.createControlledSession(
        server,
        attendees,
        "Food supply and town defenses",
        ControlledConversationOptions.allowAddonTools(Set.of("meetings:record_vote")));

// Movement remains addon-owned. Ask for the turn only after Alice reaches the podium.
var podium = ControlledAudioAnchor.at(alice.level().dimension(), podiumCenter);
meeting.requestTurn(alice, "Give your view on food supply", podium)
        .thenAccept(result -> {
            // This continuation runs on the Minecraft server thread.
            if (result.completed()) {
                // Audible playback is finished; it is now safe to grant the next speaker the floor.
            } else {
                // result.failureReason() distinguishes unavailable/capacity/closed/unsupported cases.
                // Never leave external floor state waiting merely because a turn failed.
            }
        });

meeting.addPlayerStatement(player, "What should we build first?");
meeting.setAgenda("Food supply, then guard staffing"); // affects later turns only
meeting.interruptTurn(); // immediate barge-in/cancel; stale provider/audio callbacks are ignored

for (ConversationTranscriptEntry entry : meeting.transcript()) {
    // Stable speaker UUID, kind/name, text and game tick for UI/minutes/persistence.
}

meeting.end(ControlledConversationSession.EndReason.COMPLETED);
```

`meeting.sessionId()` is stable for the whole meeting. Every `ControlledTurnResult` contains that
session ID plus a unique `turnId()`, so orchestration can reject stale work without relying on citizen
names. Only one turn may be active. A non-participant, an unavailable/unloaded speaker, exhausted
capacity, an already-active floor, a closed session, and an unsupported operation have typed results.
Provider/network failures are also terminal results rather than leaked busy state.

A turn future completes on the **Minecraft server thread** only after audible playback reaches its
terminal state. Provider generation completion alone is not audible completion. `interruptTurn()`
completes the current turn as `INTERRUPTED`, cancels playback/provider work, leaves the meeting open,
and ignores every late completion for that turn. `end(reason)` is idempotent, cancels an active turn,
and prevents late work from reopening the session. A direct player conversation may preempt the
controlled speaker; that exact turn reports interruption and ordinary player-conversation priority
remains unchanged. Controlled provider sessions are never promoted in-place into player sessions, so
meeting prompt/tool identity cannot leak into the replacement direct conversation. Cancellation is
matched by `sessionId` and `turnId`, preventing delayed caller work from cancelling a newer turn.
Idle as well as active controlled sessions transition to `SERVER_SHUTDOWN` during server teardown.

Without an audio anchor, the voice uses the citizen entity channel and follows the moving speaker.
A `ControlledAudioAnchor` creates fixed locational audio in the speaker's current dimension, suitable
for a podium/microphone. Cross-dimension podium routing is intentionally unsupported and returns a
typed failure. Listener range continues to use Talking Colonists' configured citizen voice distance.

Controlled sessions also snapshot `PromptSessionContext` per requested turn. Prompt contributors see
the stable controlled `sessionId`, `turnId`, and agenda for that turn; later `setAgenda(...)` calls do
not mutate an in-flight prompt. `ControlledConversationOptions` defines the addon-tool allow-list.
Only allowed addon tools are advertised **and** executable for controlled turns. `AiToolContext`
reports the same controlled `sessionId()` and `turnId()`; built-in core tools continue to enforce their
normal core policy. Use `noAddonTools()` for meetings that should expose no addon tool surface, or
`allAddonTools()` only when the orchestrator intentionally grants every registered addon tool.

## Pregenerated speech

Core owns pregeneration caches, playback interruption, barge-in, queue draining and takeover. Addons
may modify the prompt contract without accessing those internals:

```java
var registration = PregenerationPromptService.registerModifier(
        "my_addon:greeting_style",
        100,
        (context, prompt) -> prompt + " Keep the line appropriate for a formal ceremony.");
```

Do not inspect `PregenerationPlayback`, background-slot maps or `GeminiStream` queues.

## What remains internal by design

The following are intentionally not extension points:

- Gemini/provider websocket clients and reconnect state.
- Audio queues, Opus decoders, voice-chat channels and playback drain logic.
- Foreground/background slot maps and eviction bookkeeping.
- Pregeneration cache entries and playback objects.
- Internal memory objects/session tokens/compaction tasks.
- Urgent-contact walking state and watchdog maps.

If addon functionality requires one of those details, the supported API should gain a semantic
operation or event while the raw object remains internal. Addon compatibility is tied to gameplay
contracts and does not depend on Talking Colonists implementation choices.
