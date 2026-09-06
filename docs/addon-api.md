# Addon API

Talking Colonists treats addons as a first-class integration surface. Supported addon code lives
under `me.sshcrack.mc_talking.api`; `ConversationManager`, websocket clients, audio queues, handlers,
`duck` interfaces, pregeneration caches and other implementation packages are not API.

This document describes **API generation 2**, the breaking baseline introduced to replace the legacy
flat prompt snapshot, isolate MineColonies compatibility behind Talking Colonists-owned types, and remove
provider-specific declaration types.
`TalkingColonistsApi.API_MAJOR_VERSION` is `2`.

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

The old giant constructible `CitizenPromptView` record was replaced by a read-only grouped interface.
The Talking Colonists-owned compatibility enums remain intentionally part of the API: they form a stable
firewall between addon code and MineColonies patch-level API churn.

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
- `memories()` — immutable Talking Colonists memory snapshot when present.

Talking Colonists owns semantic categories such as `WORKING`, `EATING` and `MOURNING`. Exact state
concepts needed by addons are exposed through Talking Colonists-owned compatibility enums
(`CitizenAIState`, `AIWorkerState`, `MinimalAISubState`). Addons therefore get autocomplete, exhaustive
switches and typo safety without linking their API contract to MineColonies enum classes. Unknown/new
MineColonies values map to `UNKNOWN` at runtime; Talking Colonists' compatibility tests deliberately fail
when a newly selected MineColonies version contains an unmapped known value, so only Talking Colonists
needs updating. `CitizenStatusType`, `HappinessModifierType`, and `CitizenSkill` follow the same pattern.

## Prompt extensions

Most addons should contribute bounded context instead of replacing the full prompt:

```java
var registration = CitizenPromptService.registerContributor(
        "my_addon:expedition",
        100,
        (view, target) -> List.of(PromptContribution.observation(
                "Expedition state",
                view.identity().name() + " returned with two chorus flowers.")));
```

Contributors run in ascending `order`, then namespaced ID. A failing contributor is isolated from
core and other addons. Per-contribution and total addon text budgets are bounded.

Use:

- `observation` for current server-verified facts.
- `recollection` for remembered/inferred facts.
- `instruction` for addon-owned conversational guidance, never to bypass core permissions.

An integration that deliberately owns the complete prompt can register a provider override:

```java
var registration = CitizenPromptService.registerProvider(
        "my_addon:provider",
        100,
        myProvider);
```

The highest provider priority wins; ties are deterministic by namespaced ID. Closing that handle
falls back to the next provider or the Talking Colonists default. There is no mutable global
`setProvider/resetProvider` singleton API anymore.

## AI tools

Register addon tools rather than reflecting into built-in tool maps:

```java
var registration = AiToolRegistry.register("my_addon", "come_here", new AiTool() {
    @Override
    public String description() {
        return "Ask this citizen to come to the authenticated player.";
    }

    @Override
    public AiToolScope scope() {
        return AiToolScope.PLAYER_CONVERSATION;
    }

    @Override
    public AiToolParameter parameters() {
        return AiToolParameter.object(Map.of(
                "urgency", AiToolParameter.enumeration(List.of("normal", "urgent"), false)
        ));
    }

    @Override
    public boolean canExecute(AiToolContext context) {
        ServerPlayer player = context.player();
        return player != null
                && player.getUUID().equals(context.colony().getPermissions().getOwner());
    }

    @Override
    public JsonObject execute(AiToolContext context, JsonObject parameters) {
        context.runOnServerThread(() -> {
            // World mutation here.
        });

        JsonObject result = new JsonObject();
        result.addProperty("accepted", true);
        return result;
    }
});
```

`AiToolParameter` is Talking Colonists-owned and provider-neutral. Core translates it into the
current provider's function declaration internally.

`AiToolContext.player()` is authoritative: it is resolved from the owning conversation, not model
JSON. Use `PLAYER_CONVERSATION` plus `requirePlayer()` for player-authorized actions. Tool callbacks
are not guaranteed to originate on the Minecraft server thread, so `runOnServerThread` and
`supplyOnServerThread` are provided for world work.

The provider-facing function name derived from an addon ID is private implementation detail. Persist
only your namespaced addon/tool ID.

## Conversation eligibility and policy

Addon state can veto speech without mixing into random-conversation handlers or need assessment:

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

Player conversation starts return a typed immediate result instead of an ambiguous boolean:

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
`activeKind(citizen)` and `requestGracefulEnd(citizen)` cover common queries/actions without
exposing provider clients or manager collections.

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

## Memories

Use `CitizenMemoryService` instead of casts to Talking Colonists data-extension interfaces:

```java
CitizenMemoryService.addEvent(citizen, "I returned from the End expedition safely.");
CitizenMemoryService.addFact(citizen, "My expedition partner is Marta.");
CitizenMemoryService.addRelationshipChange(
        citizen,
        player.getUUID(),
        CitizenRelationshipDimension.TRUST,
        0.2f);

var snapshot = CitizenMemoryService.snapshot(citizen);
for (CitizenRelationshipView relationship : snapshot.orElseThrow().relationships()) {
    // targetId(), dimension(), factor()
}

CitizenMemoryService.removeFact(citizen, "My expedition partner is Marta.");
```

Relationship dimensions are Talking Colonists-owned semantic values shared by core and addons; the
internal memory implementation no longer carries a duplicate enum. Relationship deltas use the same
finite `[-1, 1]` validation as model-generated changes. Exact fact/event removal lets addon-owned
state be corrected without direct collection access. Memory storage, compaction, session tokens,
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

The addon owns attendance, navigation, seats, podiums, hand raising and floor policy. Talking
Colonists owns provider capacity, one current speaker, prompt grounding, audible completion and
cancellation:

```java
var meeting = CitizenConversationService.createControlledSession(
        server,
        attendees,
        "Food supply and town defenses");

// Move Alice to the podium first, then request the turn.
meeting.requestTurn(alice, "Give your view on food supply")
        .thenAccept(result -> {
            if (result.completed()) {
                // Grant the next speaker the floor.
            }
        });

meeting.addPlayerStatement(player, "What should we build first?");
meeting.setAgenda("Food supply, then guard staffing");
meeting.interruptTurn();

for (ConversationTranscriptEntry entry : meeting.transcript()) {
    // Structured speaker kind/id/name, text and game tick for UI/minutes/persistence.
}

meeting.end();
```

Only registered participants may receive a turn and only one controlled turn is active at once.
The transcript is bounded and speaker-attributed. Opening a meeting does not create one provider
connection for every silent attendee.

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

If addon functionality requires one of those details, add a semantic API operation/event instead of
making the internal object public. This keeps addon compatibility tied to gameplay contracts rather
than Talking Colonists implementation choices.
