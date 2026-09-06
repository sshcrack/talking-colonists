# Addon integration API

Talking Colonists exposes supported addon seams under `me.sshcrack.mc_talking.api`.
Addons should not depend on `manager`, `duck`, `pregen`, `handler`, or raw
`ConversationManager` collections. Those packages remain implementation details even where an
older public method still exists for compatibility.

The compile-only examples in
`src/test/java/me/sshcrack/mc_talking/api/examples/AddonApiCompileExample.java` are kept in the
normal test source set so API examples fail the build if signatures drift.

## Compile-time dependency and source boundary

The supported Java surface lives in `src/api/java` and is compiled as its own `addonApi` Gradle
source set. The implementation lives in `src/main/java`. `addonApi` receives the loader's mapped
external dependencies but **not** `main` output, so importing a Talking Colonists implementation
class from API source is a compile-time error instead of merely a packaging convention.

Each loader publishes a separate Maven artifact:

```text
me.sshcrack:mc_talking-api:<matching Talking Colonists version/Minecraft/loader>
```

Addon projects should use that artifact for their compiler/IDE, for example:

```kotlin
dependencies {
    compileOnly("me.sshcrack:mc_talking-api:<matching-version>")
}
```

Install and declare the normal Talking Colonists mod as the required runtime mod dependency. Avoid
using the full `mc_talking` mod jar as the addon compile surface when the API artifact is available;
the full runtime jar intentionally contains both API and implementation classes, while the API jar
contains only `me.sshcrack.mc_talking.api/**`.

Some supported signatures intentionally expose public types from Minecraft, MineColonies, Gson and
Gemini Live Lib. Addons still need the matching normal dependencies for those types. The isolation
rule is specifically that Talking Colonists' own implementation packages never become addon API.

## AI tools

Register tools with `AiToolRegistry` instead of reflecting into `AITools` maps:

```java
var toolRegistration = AiToolRegistry.register("my_addon", "come_here", new AiTool() {
    @Override
    public String description() {
        return "Ask this citizen to come to the player.";
    }

    @Override
    public AiToolScope scope() {
        return AiToolScope.PLAYER_CONVERSATION;
    }

    @Override
    public boolean canExecute(AiToolContext context) {
        var player = context.player();
        return player != null
                && player.getUUID().equals(context.colony().getPermissions().getOwner());
    }

    @Override
    public JsonObject execute(AiToolContext context, JsonObject parameters) {
        var player = context.requirePlayer();
        // Schedule world mutation on player.getServer() when necessary.
        var result = new JsonObject();
        result.addProperty("accepted", true);
        return result;
    }
});
```

The addon ID is namespaced (`my_addon:come_here`). Talking Colonists derives a provider-safe
function name internally. Do not persist or hard-code the derived Gemini name.

`AiToolContext.player()` is resolved from the actual owning conversation. Model JSON cannot choose
its actor, player UUID, colony rank, or authority. `canExecute` is checked again when the call is
executed. Gemini callbacks are not guaranteed to run on the Minecraft server thread; schedule world
mutations onto the server thread.

## Prompt contributions and verified addon state

Use `CitizenPromptService.registerContributor` instead of replacing or injecting into the assembled
prompt:

```java
var registration = CitizenPromptService.registerContributor(
        "my_addon:expedition",
        100,
        (view, target) -> List.of(PromptContribution.observation(
                "Voyager expedition",
                "The last expedition returned with two chorus flowers.")));
```

Contributors coexist with the legacy `CitizenPromptProvider` override. They run deterministically by
`order`, then namespaced ID. One contributor throwing does not disable core or other addons. Each
block is bounded, and the total addon contribution budget is bounded.

Use `observation` for current server-verified state and `recollection` for remembered/inferred state.
Current observations are explicitly labelled as higher-confidence context. `instruction` is
available for addon-owned conversational rules; do not use it to replace core safety or tool
permission checks.

## Conversation eligibility and urgent contact

Addon gameplay state can veto automatic speech without mixing into the random-conversation or need
assessor code:

```java
var speech = CitizenConversationRules.registerSpeechPolicy(
        "my_addon:military_duty",
        100,
        (citizen, kind) -> kind == ConversationKind.PLAYER || !isOnMilitaryDuty(citizen));

var urgency = CitizenConversationRules.registerUrgencyModifier(
        "my_addon:promises",
        100,
        (citizen, currentWeight) -> hasPatientOpenPromise(citizen) ? 0.0 : currentWeight);
```

Speech policies are veto-only: an addon cannot bypass sleeping, busy, cooldown, or other core
invariants. Urgency modifiers receive the already-calculated core weight, so addons do not need to
reflect configuration values such as `blockingTaskUrgencyMultiplier` or duplicate core need logic.

For ambient/group chatter that should only run when somebody can hear it, use
`CitizenConversationService.hasPlayerNearby(citizen, range)` instead of importing
`ConversationManager`.

## Reserving a citizen for addon gameplay

Use an ownership-safe reservation instead of balancing `ConversationManager.markBusy` and
`markNotBusy` calls:

```java
Optional<CitizenActivityReservation> reservation =
        CitizenConversationService.reserveActivity(citizen, "my_addon:delivery");

// Keep the returned handle with the errand/task.
reservation.ifPresent(CitizenActivityReservation::close);
```

A stale handle cannot clear a newer activity because each reservation has an opaque ownership token.
A direct player conversation is still allowed to take priority while an addon gameplay reservation
exists; after the player leaves, the reservation continues to represent the addon task until closed.

## Memories

Use `CitizenMemoryService` instead of casting MineColonies citizen data to
`CitizenDataMemoryExtended`:

```java
CitizenMemoryService.addEvent(citizen, "I returned from the End expedition safely.");
CitizenMemoryService.addFact(citizen, "My expedition partner is Marta.");
var snapshot = CitizenMemoryService.snapshot(citizen.getCitizenData());
```

These methods preserve the current simple fact/event save format. They are suitable for an addon
recording a confirmed gameplay outcome. More detailed provenance fields may be added compatibly in a
later format revision.

## Ending a conversation

Do not fetch a `GeminiWsClient` just to close it:

```java
CitizenConversationService.requestGracefulEnd(citizen);
```

A graceful end finishes the current generated turn, flushes pending audio, waits for audible
playback, then closes. It has a bounded timeout. Late generated audio after the requested final turn
is discarded so a tool-driven goodbye is not repeated.

## Ordinary citizen-to-citizen conversation

For an autonomous pair:

```java
var conversation = CitizenConversationService.createPairConversation(server, alice, bob);
conversation.setStateListener(state -> { /* server-thread callback */ });
conversation.start();
```

The handle hides Gemini clients, audio streams, slot reservations, and busy bookkeeping.

## Controlled meetings / councils

For a meeting, the caller owns attendance, navigation, seating, hand raising and the podium. Talking
Colonists owns speech generation/playback and only reserves provider capacity for the current
speaker:

```java
var meeting = CitizenConversationService.createControlledSession(
        server, attendees, "Food supply and town defenses");

// Colony Meetings moves Alice to the podium first.
meeting.requestTurn(alice, "Give your view on the food-supply item")
        .thenAccept(result -> {
            // COMPLETED means the audible turn finished, not only generation.
            // Now it is safe to grant the floor to the next citizen.
        });

meeting.addPlayerStatement(player, "What should we build first?");
meeting.setAgenda("Food supply, then guard staffing");
meeting.interruptTurn(); // barge-in / chair revokes the floor
meeting.end();
```

Only registered participants may be given the floor and only one controlled turn may be active.
The session maintains a bounded, speaker-attributed shared transcript so later turns know what was
actually said. Opening a meeting does not open one Live connection per attendee.

Current limitation: controlled turns are separate bounded provider turns tied together by Talking
Colonists' shared transcript, rather than one permanently-open multi-speaker Gemini session. This is
intentional to keep silent attendees from consuming provider capacity.

## Pregenerated speech

Talking Colonists now owns cached-playback interruption and makes reusable greeting prompts
independent of the current time/weather. Addons that need an additional constraint can register a
prompt modifier:

```java
var registration = PregenerationPromptService.registerModifier(
        "my_addon:greeting_style",
        100,
        (context, prompt) -> prompt + " Keep the line appropriate for a formal ceremony.");
```

Do not inspect `PregenerationPlayback` maps or `GeminiStream` queues. Player barge-in and higher
priority conversation takeover are handled by core.

## Registration lifetime

Tool, prompt, speech-policy, urgency and pregeneration registrations return `AutoCloseable` handles.
They remain registered until the handle is closed. This is process/mod lifetime by default; do not
close them merely because one integrated server stops unless your addon re-registers on the next
server start.
