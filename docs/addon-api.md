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
- `memories()` — immutable Talking Colonists memory snapshot when present.

Talking Colonists owns semantic categories such as `WORKING`, `EATING` and `MOURNING`. Exact state
concepts needed by addons are exposed through Talking Colonists-owned compatibility enums
(`CitizenAIState`, `AIWorkerState`, `MinimalAISubState`). Addons therefore get autocomplete, exhaustive
switches and typo safety without linking their API contract to MineColonies enum classes. Unknown/new
MineColonies values map to `UNKNOWN` at runtime; Talking Colonists' compatibility tests deliberately fail
when a newly selected MineColonies version contains an unmapped known value, so only Talking Colonists
needs updating. `CitizenStatusType`, `HappinessModifierType`, and `CitizenSkill` follow the same pattern.

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

Addon tools are registered with `AiToolRegistry`:

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

## Memories

Citizen memory is accessed through `CitizenMemoryService`:

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

Relationship dimensions are Talking Colonists-owned semantic values shared by core and addons.
Relationship deltas use finite `[-1, 1]` validation. Exact fact/event removal supports correction of
addon-owned state. Memory storage, compaction, session tokens, broadcast/rumor propagation and save
coordination are core responsibilities.

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

If addon functionality requires one of those details, the supported API should gain a semantic
operation or event while the raw object remains internal. Addon compatibility is tied to gameplay
contracts and does not depend on Talking Colonists implementation choices.
