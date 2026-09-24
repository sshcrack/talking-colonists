# Addon API

## Provider readiness

`CitizenConversationService.providerStatus(citizen)` returns an immutable, credential-free
`Optional<ProviderSessionStatus>` for the current foreground Live client. Query it on the server
thread. `state()` distinguishes startup, active operation, recovery, and terminal states;
`readyForInput()` also checks transport/setup readiness; `recoveryAttempts()` counts retries.
Empty means no foreground Live client, not a provider error. Background/Flash work is not included.
The snapshot can immediately become stale; it is not an ownership reservation or delivery guarantee.
Use lifecycle listeners for gameplay ownership changes, not `ACTIVE` as a synonym for those events.

Talking Colonists treats addons as a first-class integration surface. Supported addon code lives
under `me.sshcrack.mc_talking.api`; `ConversationManager`, websocket clients, audio queues, handlers,
`duck` interfaces, pregeneration caches and other implementation packages are not API.

This document describes the supported **API generation 2** addon contract.
`TalkingColonistsApi.API_MAJOR_VERSION` is `2`. Migration guidance is maintained separately in
[addon-migration.md](addon-migration.md).

**Starting a new addon?** Use the
[addon template](https://github.com/sshcrack/talking-colonists-addon-template) ("Use this template"
on GitHub). It builds for 1.21.1 NeoForge and 1.20.1 Forge and shows feature detection, a prompt
contributor, a query tool and a lifecycle listener. It targets Talking Colonists 2.1.

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

For example, the current API generation 2 beta for Talking Colonists `2.0.0-beta.1`, Minecraft `1.21.1`, NeoForge uses:

```kotlin
compileOnly("me.sshcrack:mc_talking-api:2.0.0-beta.1-1.21.1-neoforge")
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

## Feature detection

`TalkingColonistsApi.API_MAJOR_VERSION` (`2`) is a breaking-change generation: addons must match it
exactly. `TalkingColonistsApi.API_MINOR_VERSION` is additive within generation 2 and only ever grows.
Track A tasks each add one `ApiFeature` constant (`BROADCAST_PUBLISHING`, `COLONY_EVENTS`,
`TEXT_GENERATION`, `PLAYER_TEXT_INPUT`, `UTTERANCE_EVENTS`, `PLAYER_CONVERSATION_OPTIONS`,
`PROVIDER_BUDGET`, `VISITOR_SPEAKERS`, `CROSS_COLONY_SESSIONS`, `PLAYER_SPEECH_CAPTURE`) the moment
the task starts landing; a constant only flips from unsupported to supported once its feature is
fully implemented, so `TalkingColonistsApi.supports(ApiFeature.X)` reliably means "safe to call X's
entry points right now," not merely "this API generation knows the name X."

```java
TalkingColonistsApi.Services services = TalkingColonistsApi.services();
int minor = services.apiMinorVersion();
boolean broadcastReady = TalkingColonistsApi.supports(ApiFeature.BROADCAST_PUBLISHING);
```

### API 2.1 at a glance

Every 2.1 feature is implemented in Talking Colonists 2.1.0 (`API_MINOR_VERSION` 1). The compile-checked
example for each one is in `src/apiTest/.../Api21FeaturesExample.java`.

| `ApiFeature` | Roadmap | Entry points | Section |
|---|---|---|---|
| `BROADCAST_PUBLISHING` | A1 | `CitizenMemoryService.publishBroadcast`, `retractBroadcast` | [Publishing colony broadcasts](#publishing-colony-broadcasts-api-21-apifeaturebroadcast_publishing) |
| `COLONY_EVENTS` | A2 | `ColonyEventService.recent`, `record`, `registerListener` | [Colony event feed](#colony-event-feed-api-21-apifeaturecolony_events) |
| `TEXT_GENERATION` | A3 | `CitizenTextService.generate`, `generateColonyVoice` | [In-character text generation](#in-character-text-generation-api-21-apifeaturetext_generation) |
| `PLAYER_TEXT_INPUT` | A4 | `CitizenConversationService.sendPlayerText`, `addContext` | [Player text input](#player-text-input-api-21-apifeatureplayer_text_input) |
| `UTTERANCE_EVENTS` | A5 | `CitizenConversationService.registerUtteranceListener` | [Utterance events](#utterance-events) |
| `PLAYER_CONVERSATION_OPTIONS` | A6 | `CitizenConversationService.startPlayerConversation(player, citizen, options)` | [Starting speech and conversations](#starting-speech-and-conversations) |
| `PROVIDER_BUDGET` | A7 | `ProviderBudgetService.snapshot`, `config`, `registerQuotaListener`, `reloadConfig` | [Provider capacity and quota](#provider-capacity-and-quota) |
| `VISITOR_SPEAKERS` | A8 | `CitizenConversationRules.registerVisitorPolicy`, `CitizenPromptView.visitor()` | [Visitors](#visitors-tavern-guests) |
| `CROSS_COLONY_SESSIONS` | A9 | `createControlledSession` with attendees from several colonies, `ControlledSessionRejectedException` | [Controlled meetings and councils](#controlled-meetings-and-councils) |
| `PLAYER_SPEECH_CAPTURE` | A10 | `PlayerSpeechCapture.capture`, `cancel`, `isCapturing` | [Player speech capture](#player-speech-capture-api-21-apifeatureplayer_speech_capture) |

### Why this exists

At runtime, `TalkingColonistsApi` and every other API class are loaded from the **installed**
Talking Colonists mod jar, never from the addon's compile-time `mc_talking-api` dependency. An addon
compiled against API 2.1 that calls a brand-new static method on a 2.0 runtime gets
`NoSuchMethodError`, not a friendly "unsupported" result — the method, or even the whole
`ApiFeature` class, simply is not present in that older jar. `Services.apiMinorVersion()` and
`Services.supports(ApiFeature)` are **default interface methods**, so an older `Services`
implementation that never heard of them still satisfies the (recompiled) newer interface and answers
`0` / `false` — every feature reports unsupported on a runtime that predates it. That default-method
resolution is what makes `TalkingColonistsApi.supports(...)` itself safe to call on any 2.x runtime
that already has `ApiFeature`. It does not, by itself, help a 2.0 runtime that predates `ApiFeature`
and `supports(...)` entirely; see the two safe patterns below for that case.

### Pattern 1 — minimum Talking Colonists version dependency (works on 2.0, no code needed)

If the addon only needs to require a minimum Talking Colonists version, declare that dependency
through the loader's own mod metadata (Forge/NeoForge `mods.toml` version range on the
`mc_talking` mod ID). The loader refuses to start the addon on an older Talking Colonists instead of
throwing `NoSuchMethodError` mid-game. This is the simplest option, but it is all-or-nothing: the
addon cannot install at all on an older server, even to offer a degraded experience.

### Pattern 2 — catch `NoSuchMethodError`/`NoClassDefFoundError` (works on 2.0, degrades gracefully)

To keep the addon installable on a 2.0 runtime and merely disable the new feature there, wrap **both**
the `ApiFeature` reference and the `supports(...)` call in one `try` block, at the actual call site:

```java
static boolean broadcastPublishingAvailable() {
    try {
        return TalkingColonistsApi.supports(ApiFeature.BROADCAST_PUBLISHING);
    } catch (NoSuchMethodError | NoClassDefFoundError predatesApiFeature) {
        return false; // Talking Colonists 2.0: ApiFeature/supports(...) do not exist yet.
    }
}
```

This works because the JVM resolves symbolic references (the `ApiFeature.BROADCAST_PUBLISHING`
constant load and the `supports` method call) lazily, at the exact bytecode instruction that uses
them, not when the surrounding class is loaded. As long as both the enum reference and the method
call are textually inside the `try` block — not passed in from a caller that resolved
`ApiFeature.BROADCAST_PUBLISHING` itself before calling in — a runtime that lacks those symbols
throws `NoSuchMethodError`/`NoClassDefFoundError` right there, and this catches it instead of
crashing addon startup. A working compiled example lives in
`src/apiTest/java/me/sshcrack/mc_talking/api/examples/AddonApiCompileExample.java`
(`broadcastPublishingAvailable()`).

Do not put `ApiFeature` enum constants in `public static final` fields of a class an addon loads
eagerly at startup (e.g. a field initializer outside any `try`) — that resolves the symbol before any
`catch` can run.

### Unsupported feature entry points

Every feature entry point added by a later Track A task must fail predictably instead of behaving as
if the feature existed. When the installed runtime does not implement a feature yet, the entry point
throws `UnsupportedOperationException` with a message naming the missing `ApiFeature` and the
installed API version — call `TalkingColonistsApi.requireSupported(ApiFeature.X)` at the top of the
entry point's implementation rather than inventing another exception shape:

```java
public static SomeResult publishBroadcast(...) {
    TalkingColonistsApi.requireSupported(ApiFeature.BROADCAST_PUBLISHING);
    // ... feature implementation ...
}
```

`requireSupported` only helps once the addon can already reach that call safely (i.e. the runtime is
new enough for `ApiFeature`/`requireSupported` to exist at all); guard the outer call with Pattern 1
or Pattern 2 above first when the addon must also support a 2.0 runtime.

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

UUID citizenId = snapshot.citizenId();
UUID playerId = snapshot.playerId(); // nullable when no player context is attached
String name = snapshot.identity().name();
String job = snapshot.work().jobName();
CitizenActivityCategory category = snapshot.activity().category();
AIWorkerState exactWorkState = snapshot.activity().workState();
String activityText = snapshot.activity().description();
```

Top-level `citizenId()` always identifies the citizen represented by the snapshot. `playerId()` is the
authoritative contextual/authenticated player UUID when one exists, including controlled turns bound
by `addPlayerStatement(...)`; it is `null` for NPC/system-only prompt contexts.

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

### Provider-facing tool names

Registration and allow-lists continue to use the stable public `namespace:name` ID. If prompt text or a
tool description must mention the exact function name Gemini sees, derive it through the public helper
instead of duplicating the encoding scheme:

```java
String providerName = AiToolRegistry.providerName("errands", "take_job");
// Or, when you already have a validated ID:
String sameName = AiToolRegistry.providerName(new NamespacedAddonId("errands", "take_job"));
// tc_7_errands_take_job
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
player authority. In a controlled session, `addPlayerStatement(serverPlayer, ...)` explicitly binds
that server-side player as authority for turns requested afterward. Real players are refreshed from
the current online player list at execution time, while loader-provided fake players remain valid
automation actors; this does **not** enable microphone input, listening presentation, or player-only
built-in tools.

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

## Provider capacity and quota

Since API 2.1 (`ApiFeature.PROVIDER_BUDGET`), addons can check capacity before scheduling expensive work,
and can explain honestly when citizens cannot talk:

```java
ProviderBudgetView budget = ProviderBudgetService.snapshot();
if (budget.background().available() > 0
        && budget.model(ProviderBudgetService.config().textModel())
                .map(m -> m.state() == ProviderQuotaState.OK).orElse(false)) {
    // e.g. generate tonight's newspaper
}

ProviderBudgetService.registerQuotaListener("gazette:quota", 0, (previous, current) -> {
    // Runs on the server thread when a model becomes exhausted or recovers.
});
```

- `ProviderBudgetService.config()` replaces reflective reads of the config class. It reports whether an
  API key is set, the Live and text models, and the blocking-task urgency multiplier.
- `reloadConfig()` reloads the config file.
- Quota changes are checked about once a second.

## Visitors (tavern guests)

MineColonies visitors are not conversation participants by default: eligibility reports
`ConversationEligibility.Status.VISITOR`. Since API 2.1 (`ApiFeature.VISITOR_SPEAKERS`), an addon such as a
tavern recruiter can opt them in for the conversation kinds it needs:

```java
AddonRegistration tavernTalk = CitizenConversationRules.registerVisitorPolicy(
        "tavern:talk", 0, (visitor, kind) -> kind == ConversationKind.PLAYER);
```

- A visitor's prompt view has no job, home, family, requests or happiness modifiers.
- `CitizenPromptView.visitor()` gives the recruit cost and how many colony days the visitor has stayed.
- Visitors keep a short-term memory of their 10 newest facts and events. It moves with them when they
  are recruited.
- Veto-only speech policies still apply to visitors. Core ambient chatter never picks visitors on its
  own.

## Utterance events

Since API 2.1 (`ApiFeature.UTTERANCE_EVENTS`), addons can follow what is said in every conversation kind:

```java
CitizenConversationService.registerUtteranceListener("court:testimony", 0, event -> {
    // Server thread. One event per finished utterance, never partial transcription chunks.
    if (event.speaker() == ConversationUtteranceEvent.Speaker.PLAYER) {
        // event.speakerId(), event.text(), event.kind(), event.sessionId() ...
    }
});
```

- Player speech arrives as one utterance once the citizen answers.
- A citizen's reply arrives once its audio has been heard in full.
- Pair conversations report each scripted line after playback, with source `SCRIPT`.
- Controlled-session player statements and lines sent with `sendPlayerText` arrive as `TYPED`.
- Nothing is reported after a session ends.

Transcription is best effort. Speech-to-text can mishear, and a citizen's words are only what the model
said. Never treat an utterance as proof for irreversible gameplay; confirm such actions through your own
AI tool.

## Starting speech and conversations

Player conversation starts return a typed immediate result:

```java
ConversationStartResult result =
        CitizenConversationService.startPlayerConversation(player, citizen);

if (!result.started()) {
    // PROVIDER_UNAVAILABLE, IN_USE_BY_OTHER_PLAYER, CAPACITY_EXHAUSTED, ...
}
```

Since API 2.1 (`ApiFeature.PLAYER_CONVERSATION_OPTIONS`), an addon can start a scoped conversation, such
as a quest giver or judge, without changing global prompts:

```java
if (TalkingColonistsApi.supports(ApiFeature.PLAYER_CONVERSATION_OPTIONS)) {
    PlayerConversationOptions options = PlayerConversationOptions.defaults()
            .withAgenda("Ask the player to find your lost cat and promise a loaf of bread as a reward.")
            .withTools(ControlledConversationOptions.allowAddonTools(Set.of("quests:give_quest")))
            .withPurpose("quests:lost_cat")
            .withMemoryExtraction(true);
    CitizenConversationService.startPlayerConversation(player, citizen, options);
}
```

The agenda and tool allow-list apply only to that session and are never stored. The purpose appears as
`ConversationLifecycleEvent.purpose()` on that session's STARTED and ENDED events. If the citizen is
mumbling, a non-default request replaces that session instead of taking it over.

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

## Player text input (API 2.1, `ApiFeature.PLAYER_TEXT_INPUT`)

An addon can put text into a player's live direct conversation. This helps players without a
microphone, and lets gameplay events reach the citizen:

```java
if (TalkingColonistsApi.supports(ApiFeature.PLAYER_TEXT_INPUT)) {
    // A line the player typed: the citizen answers it as if it had been spoken.
    PlayerTextResult sent = CitizenConversationService.sendPlayerText(player, citizen, "Can you bake bread?");

    // Something that just happened: marked as a game event, never as the player's words.
    CitizenConversationService.addContext(player, citizen, "The player just handed you the deed to the bakery.");
}
```

- **Ownership:** only the player who owns the direct conversation with `citizen` can send into it.
  Anyone else gets `NOT_IN_CONVERSATION`. Pass the authenticated player from your command, packet
  or chat handler, never a name or UUID the model produced.
- **Delivery:**
  - `sendPlayerText` sends a player turn right away, so it can interrupt the citizen like speech
    does. If the session is still connecting, the turn is queued.
  - The line is recorded under the player's name for memory extraction, and reported to utterance
    listeners with source `TYPED`.
  - `addContext` waits until the citizen finishes its current turn, and the citizen usually
    reacts to it. It is neither recorded as speech nor replayed after a reconnect.
- **Limits:**
  - Text is trimmed and line breaks collapse to spaces. `EMPTY` or `TOO_LONG` (over
    `PlayerTextResult.MAX_CHARS`, 500 characters).
  - At most 5 lines and 5 notes per player every 10 seconds (`RATE_LIMITED`).
- **Threading:** call on the server thread. The result is immediate.

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

### Publishing colony broadcasts (API 2.1, `ApiFeature.BROADCAST_PUBLISHING`)

Addons can publish the same colony broadcasts a player creates by asking a citizen to spread the
word (the `initiate_broadcast` AI tool). Both paths share one runtime, so length limits, the
per-colony rate limit, propagation, and announcing aloud behave the same way.

```java
if (TalkingColonistsApi.supports(ApiFeature.BROADCAST_PUBLISHING)) {
    var source = BroadcastSource.block(noticeBoardPos, "the notice board");
    var result = CitizenMemoryService.publishBroadcast(colony,
            BroadcastRequest.fromPosition(source, "Harvest festival at the town hall tonight!", noticeBoardPos)
                    .withExpiry(Duration.ofHours(2)));
    if (result.isPublished()) storeForLater(result.broadcastId());
}
// Later, e.g. when the notice is taken down:
CitizenMemoryService.retractBroadcast(colony, broadcastId);
```

- **Scope:** `BroadcastRequest.immediate(...)` (`COLONY_IMMEDIATE`) gives the broadcast to every
  citizen now. `fromCitizen(...)` / `fromPosition(...)` (`PROPAGATE_FROM`) give it to one citizen,
  or to the loaded citizens within `broadcastPropagationRange` of a position. It then spreads through
  normal citizen-to-citizen propagation.
- **Source and provenance:** `BroadcastSource.player/addon/block`. Citizens attribute addon and
  block broadcasts to the source's display name ("the notice board announced: ..."), recorded as
  `MemoryProvenance.ADDON_DIRECT_WRITE`. Player broadcasts keep the "X sent word via Y" wording and
  `PLAYER_STATEMENT`. `CitizenBroadcastMemoryView` exposes `provenance()`, `sourceLabel()` and
  `expiresAtMs()`.
- **Expiry and voicing:** `withExpiry(duration)` makes citizens forget it; expired broadcasts leave
  prompts and stop spreading. `withAnnounceAloud(false)` keeps it out of the spoken announcements
  that carriers make near players (server config `enableBroadcastYelling` still applies).
- **Limits:** messages are at most `BroadcastRequest.MAX_MESSAGE_LENGTH` (500) characters; invalid
  requests throw `IllegalArgumentException`. Each colony can publish 10 broadcasts per 10 minutes
  (shared with the AI tool). Beyond that, the result is `RATE_LIMITED`. Other results:
  `DISABLED` (server turned broadcasts off), `NO_RECIPIENTS` (unknown origin citizen or nobody near
  the position), `UNAVAILABLE` (colony not loaded on a server).
- **Retraction:** `retractBroadcast` makes every citizen of the colony forget it and returns
  whether anyone still remembered it.

Calls are marshalled to the server thread like the other memory operations.

## Colony event feed (API 2.1, `ApiFeature.COLONY_EVENTS`)

`ColonyEventService` exposes the colony events citizens already talk about (raids, deaths, births,
hires, job changes, buildings added, removed and upgraded, colony founded). Addons can add their own.

```java
if (TalkingColonistsApi.supports(ApiFeature.COLONY_EVENTS)) {
    // Read: newest first, within the last in-game hour
    List<ColonyEventView> news = ColonyEventService.recent(colony, Duration.ofHours(1));

    // Write: citizens mention it like any other recent event
    ColonyEventService.record(colony, new AddonColonyEvent("elections", "election_won",
            "Maria Silva won the mayoral election"));

    // Listen: every newly recorded event, core or addon, on the server thread
    AddonRegistration reg = ColonyEventService.registerListener("gazette:events", 0,
            (colony, event) -> queueForNextIssue(colony, event));
}
```

- **Distinguishing events:** `ColonyEventView.type()` is a `ColonyEventType`. Addon events are
  `ADDON` and carry `addonNamespace()` and `addonKey()`. Descriptions are the text citizens see.
- **Bounds:** core events keep the 20 most recent per colony. Addon events have a separate budget
  of the 10 most recent **per namespace**, so a chatty addon never pushes out core events or other
  addons' events. Both are saved with the colony.
- **Prompts:** addon events appear in citizens' "recent colony events" for the server's
  `colonyEventWindowSeconds`, the same as core events.
- **Listeners:** listeners run in ascending `order` on the server thread. An exception in one
  listener is logged and does not affect the others. Ids must be namespaced; close the
  registration to stop listening.

## Autonomous citizen conversations

For a regular two-citizen conversation:

```java
var conversation = CitizenConversationService.createPairConversation(server, alice, bob);
conversation.setStateListener(state -> {
    // GENERATING / PLAYING_AUDIO / ENDED
});
conversation.start();
```

The handle hides provider clients, streams, audio queues, slot ownership and teardown. Existing
two-citizen conversations remain available through this convenience handle. Addons that want the
new bounded/fair automatic-floor contract can instead open a controlled session with two or more
participants and delegate it as described below.

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

Since API 2.1 (`ApiFeature.CROSS_COLONY_SESSIONS`), attendees may come from different colonies, for
example for a peace talk or a trade council:

- Each speaker keeps its own colony's prompt, tools and permissions. A tool a speaker calls acts on
  the speaker's own colony.
- Each turn prompt names the other colonies at the session, their attendees, and how the speaker's
  colony regards each of them (its MineColonies diplomacy status, or "unknown").
- Attendees in different dimensions cannot hear each other. `createControlledSession` rejects them
  with a `ControlledSessionRejectedException` whose `reason()` is `CROSS_DIMENSION`. It extends
  `IllegalArgumentException`.


`meeting.sessionId()` is stable for the whole meeting. Every `ControlledTurnResult` contains that
session ID plus a unique `turnId()`, so orchestration can reject stale work without relying on citizen
names. Only one turn may be active. A non-participant, an unavailable/unloaded speaker, exhausted
capacity, an already-active floor, a closed session, and an unsupported operation have typed results.
Provider/network failures are also terminal results rather than leaked busy state.

### Optional autonomous group discussion

A controlled session remains manually floor-owned unless its caller **explicitly** delegates floor
selection. This keeps Colony Meetings-style orchestration exclusive by default while allowing a
small council, family, shop crew, or similar group to converse automatically when an addon wants it:

```java
AutonomousDiscussionHandle discussion = meeting.delegateAutonomousDiscussion(
        AutonomousDiscussionPolicy.defaults());

discussion.pause();  // current audible turn may finish; no new automatic turn starts
// Once the floor is idle while paused, meeting.requestTurn(...) is manual again.
discussion.resume();

discussion.completion().thenAccept(reason -> {
    // TURN_LIMIT / DURATION_LIMIT / NO_AVAILABLE_PARTICIPANTS / PROVIDER_FAILURE / STOPPED
});
```

The default policy allows at most **8 successfully audible turns**, schedules new turns for at most
**2 minutes**, and configures each automatic Gemini Live connection with
**`maxOutputTokens = 256`**. Policy construction is itself bounded: 1–64 turns, a positive duration
up to 15 minutes, and 32–2048 output tokens. The duration is checked before starting each new turn;
an already-audible turn is allowed to reach the same controlled playback-completion boundary rather
than being cut off merely because the scheduling deadline elapsed. Shared transcript retention keeps
the existing 8,000-character bound independently of provider output limits.

Automatic selection is deterministic round-robin over the supplied participant order. An automatic
speaker cannot immediately follow itself; unloaded/ineligible participants are skipped without
opening provider connections. If no fair/available participant remains, the delegation completes
with `NO_AVAILABLE_PARTICIPANTS` instead of looping. Exactly **one** provider turn is active at a
time, and the next speaker is not scheduled until the prior turn is audibly terminal and its
foreground reservation has been released. Silent attendees therefore consume no provider slots.

While the handle is `RUNNING`, manual `requestTurn(...)` calls are rejected as an already-owned floor
so automatic and caller-selected speakers cannot overlap. `pause()` prevents a subsequent automatic
turn but does not truncate audio already playing; after that turn finishes, manual floor requests are
allowed. `resume()` waits for any manual turn already in progress before automatic selection
continues. `stop()` permanently relinquishes the delegation and interrupts an automatic turn if one
is active, without ending the controlled session itself.

A direct player conversation that preempts the active automatic speaker pauses the delegation with
`PLAYER_INTERRUPTED`. A capacity race pauses it with `CAPACITY_UNAVAILABLE`. Neither condition spins
or retries indefinitely; the addon decides when to call `resume()`. Ordinary caller interruption
pauses with `CALLER`. Policy completion likewise returns floor ownership to the caller and does not
automatically end the surrounding controlled session.

Every automatic turn is still an ordinary controlled turn internally: it has the same session/turn
identity, shared attributed history, stale-callback rejection, player-interruption behavior, and
audible playback completion. Prompt contributors therefore receive the same
`PromptContributionContext.session()` and can add family/shop/group facts for the current speaker.
Talking Colonists does not infer attendance, pathing, schedules, seats, movement, or addon-specific
group membership; the caller chooses participants and supplies that context.

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
reports the same controlled `sessionId()` and `turnId()`. Calling `addPlayerStatement(player, ...)`
binds that player as authoritative for subsequently requested turns, so allowed
`PLAYER_CONVERSATION` addon tools can execute with normal permission checks. The authority UUID is
snapshotted when a turn is requested. At each tool call, a real player is re-resolved through the
server's current player list: reconnecting therefore picks up the replacement `ServerPlayer`, while a
currently disconnected real player is not allowed to execute against a stale entity object.
Loader-provided `FakePlayer` instances are deliberately retained as detached automation/test actors,
because those valid server-side players are not inserted into the online player list. This binding does
not convert the controlled session into a live player conversation; built-in core tools continue to
enforce their normal direct-conversation policy. Use `noAddonTools()` for meetings that should expose
no addon tool surface, or `allAddonTools()` only when the orchestrator intentionally grants every
registered addon tool.

## In-character text generation (API 2.1, `ApiFeature.TEXT_GENERATION`)

`CitizenTextService` writes text in a citizen's voice, or in the colony's collective voice, for
letters, notices, newspaper items and speeches. It is text only: it never plays audio and never
takes a conversation slot.

```java
if (TalkingColonistsApi.supports(ApiFeature.TEXT_GENERATION)) {
    CitizenTextService.generate(citizen, TextRequest.of("postal:letter",
                    "Write a short thank-you letter to Steve for the new well.").withMaxChars(400))
            .thenAccept(result -> server.execute(() -> {
                if (result.isSuccess()) deliverLetter(result.text());
                else logSkipped(result.status()); // QUOTA, NO_CAPACITY, INVALID_OUTPUT, ...
            }));
}
```

- **Context:** `generate` uses the citizen's normal detailed prompt context, including prompt
  contributors, and the server's configured response language. `generateColonyVoice(colony, ...)`
  uses only colony context (name, age, founder, recent events, neighbours, weather) for text that
  no single citizen writes.
- **Structured output:** `withResponseSchema(jsonSchema)` requests Gemini structured output. The
  result's `json()` holds the parsed object; output that is not a JSON object, or lacks a top-level
  `required` field, is `INVALID_OUTPUT`.
- **Results:** futures always complete with a `TextResult` and never throw: `SUCCESS`, `QUOTA`
  (Flash quota exhausted), `NO_CAPACITY` (at most 2 text requests run at once), `INVALID_OUTPUT`,
  `CANCELLED` (server stopping), `UNAVAILABLE` (no API key or no server), `PROVIDER_ERROR`.
  `maxChars` over-long text is trimmed at a sentence end.
- **Costs:** requests use `McTalkingConfig.FLASH_MODEL` (Flash-Lite; about 15 requests/minute and
  500/day on the free tier) and share its quota tracking. When Flash-Lite is out of quota, rate
  limited, or fails with a server or network error, and the `enableLiveTextFallback` option is on
  (the default), the request is answered by the cheap Live model instead. It has no daily limit on
  the free tier, but each fallback request uses one background Live session while it runs, and
  waits for one when none is free. A structured request's schema is sent in the prompt on that
  path, and the answer is still checked against it. With the fallback off, or the Live model also out of quota,
  the result is `QUOTA` as before.
- **Threading:** call on the server thread (the prompt reads live game state; other threads are
  marshalled to it). Completion happens on a worker thread, so hop back with `server.execute(...)`
  before touching the world. `purpose` (e.g. `"gazette:headline"`) appears in logs.

## Player speech capture (API 2.1, `ApiFeature.PLAYER_SPEECH_CAPTURE`)

`PlayerSpeechCapture` turns what a player says through Simple Voice Chat into text, without a
citizen, for example for a loudspeaker or microphone item.

```java
// In the item's use handler, on the server thread:
if (TalkingColonistsApi.supports(ApiFeature.PLAYER_SPEECH_CAPTURE)) {
    PlayerSpeechCapture.capture(player, Duration.ofSeconds(15))
            .thenAccept(result -> server.execute(() -> {
                if (result.isTranscribed()) announce(player, result.transcript());
                else tellPlayer(player, result.status()); // NO_SPEECH, NO_VOICE_CHAT, QUOTA, ...
            }));
}
```

- **Explicit action only:** start a capture only when the player just did something (used an
  item, clicked a block or button). Never capture continuously or in the background. While a
  capture runs, the player sees "● Listening…" above the hotbar, then "Transcribing…".
- **End of speech:** the capture ends 0.9 s after the player stops speaking. It also ends after
  6 s without any speech (`NO_SPEECH`), or at `maxDuration`, whichever comes first.
  `maxDuration` is capped at `PlayerSpeechCapture.MAX_DURATION` (30 s). Speech detection is the
  same local detector conversations use for barge-in.
- **Microphone ownership:** while the capture listens, the player's microphone audio goes only to
  it, not to a citizen conversation. This includes whispering and voice groups. Audio received
  after the end is ignored. `cancel(player)` stops a capture (`CANCELLED`).
- **Results:** futures always complete with a `SpeechCaptureResult` and never throw:
  - `TRANSCRIBED`
  - `NO_SPEECH`
  - `CANCELLED` (also when the server stops)
  - `PLAYER_LEFT`
  - `BUSY` (one capture per player)
  - `NO_VOICE_CHAT` (not connected or disabled)
  - `QUOTA`
  - `UNAVAILABLE` (no API key)
  - `PROVIDER_ERROR`
- **Costs and privacy:** each capture that heard speech makes one request to
  `McTalkingConfig.FLASH_MODEL` (Flash-Lite, 500/day on the free tier). The request carries the
  audio as 16 kHz mono WAV and shares that model's quota tracking. Captures that heard no speech
  make no request. The audio is kept only in memory until the request is sent. It uses no Gemini
  Live session.
- **Threading:** call `capture` on the server thread. Completion happens on a worker thread.
- **Trying it:** `/talking_colonists speech_capture [seconds]` (ops only) captures the running
  player and prints the transcript.

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
