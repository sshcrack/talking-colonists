# Plan: `feat/colony-foundation-and-milestones`

## Context

The mod injects colony lifecycle events into citizen prompts via `ColonyEventBuffer` (deaths, births, job changes, building changes) and colony milestones via `ColonyStatsHelper` (mob kills, buildings built, raid count). But there is no sense of **how old the colony is**, **who founded it**, or **round-number anniversaries**. Citizens never reference the colony's founding day, and no event is recorded when a colony is first created.

The `ColonyCreatedModEvent` exists in the MineColonies API but is unsubscribed. `IColony.getDay()` is available and used in `GetColonyAction` but not injected into prompts.

## Objective

Make citizens aware of their colony's age and founding story. Track the founding player name and colony creation day. Citizens reference round-number milestones (day 1, day 100, day 365), founding members, and colony longevity.

## Implementation Steps

### 1. Add `COLONY_FOUNDED` event type to `ColonyEventBuffer`

In `util/ColonyEventBuffer.java`:
- Add `COLONY_FOUNDED` to the `EventType` enum
- Add a persistent map `colonyFoundingInfo: Map<Integer, FoundingInfo>` with:
  - `foundingPlayerName: String` — name of the player who placed the colony block
  - `foundingDay: int` — value of `colony.getDay()` at creation time
  - `recorded: long` — `System.currentTimeMillis()` timestamp for event window expiry
- Add `recordFounding(int colonyId, String playerName, int day)` that stores the info and calls `recordEvent(colonyId, COLONY_FOUNDED, "...")`
- Add `getFoundingInfo(int colonyId)` returning a read-only view of the info
- `removeColony()` and `clear()` must also clean the new map

### 2. Subscribe to `ColonyCreatedModEvent`

In `listener/ColonyEventSubscriber.java`:
- Import `ColonyCreatedModEvent`
- Add a subscription: `bus.subscribe(ColonyCreatedModEvent.class, ...)` that:
  - Gets the colony (event is `AbstractColonyModEvent`)
  - Gets the first owner/founder from `colony.getPermissions().getPlayers()` (the player with owner/initial rank)
  - Calls `ColonyEventBuffer.recordFounding(colonyId, playerName, colony.getDay())`
  - Logs it

**Important**: `ColonyCreatedModEvent` fires during colony initialization, so the subscriber must be registered early (it already is via `McTalking` → `ColonyEventSubscriber.register()`).

### 3. Inject colony age into `CitizenPromptView`

In `manager/CitizenPromptViewFactory.java`:
- Add a new field to the extracted data: `colonyAgeDays` (int) using `data.getColony().getDay()`
- Add `colonyFoundingPlayer` (String) from `ColonyEventBuffer.getFoundingInfo()`
- Add both to the `CitizenPromptView` constructor call

In `api/prompt/view/CitizenPromptView.java`:
- Add `@Nullable String colonyFoundingPlayer` and `int colonyAgeDays` components (append at end, before the closing paren)
- Add Javadoc for new fields

### 4. Add anniversary milestones to `ColonyStatsHelper`

In `util/ColonyStatsHelper.java`:
- Add milestone entries for round-number colony ages:
  - Day 1: "The colony was just founded — everything is new and exciting."
  - Day 7: "We've been here for a week already."
  - Day 30: "A whole month — the colony is really settling in."
  - Day 100: "Over 100 days! This place has come so far."
  - Day 365: "A full year — who would have thought?"
- Pick the one closest to the current colony age (or all eligible, pick one at random)
- Only show if `enableColonyStatsMentions` is true (reuse the existing toggle)

### 5. Add founding player reference to `DefaultCitizenPromptProvider`

In `manager/DefaultCitizenPromptProvider.java`:
- In `addObservations()` (or a new method), add a block when `view.colonyFoundingPlayer()` is non-null:
  ```
  ## COLONY HISTORY
  - This colony was founded by {{foundingPlayer}} on day {{foundingDay}}.
  - The colony is now {{ageDays}} days old.
  ```

### 6. Add config toggle

In `config/McTalkingConfig.java`:
- Add `@AutoGen(category = "citizens", group = "colony_events")` (reuse existing group)
- `@TickBox @SerialEntry public boolean enableFoundingReferences = true;`

In `src/main/resources/assets/mc_talking/lang/en_us.json`:
- Add keys:
  - `yacl3.config.mc_talking:config.enableFoundingReferences`
  - `yacl3.config.mc_talking:config.enableFoundingReferences.desc`

## Files to Touch

| File | Change |
|------|--------|
| `util/ColonyEventBuffer.java` | Add `COLONY_FOUNDED` enum, `FoundingInfo` record, `recordFounding()`, `getFoundingInfo()`, cleanup |
| `listener/ColonyEventSubscriber.java` | Subscribe `ColonyCreatedModEvent` |
| `api/prompt/view/CitizenPromptView.java` | Add `colonyFoundingPlayer`, `colonyAgeDays` fields |
| `manager/CitizenPromptViewFactory.java` | Extract and pass founding info + colony day count |
| `manager/DefaultCitizenPromptProvider.java` | Add colony history section in observations |
| `util/ColonyStatsHelper.java` | Add age-based milestones |
| `config/McTalkingConfig.java` | Add `enableFoundingReferences` toggle |
| `lang/en_us.json` | Translations |

## MineColonies API References

- `ColonyCreatedModEvent` — `com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent` (extends `AbstractColonyModEvent` with `getColony()`)
- `IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyCreatedModEvent.class, handler)` — subscribe pattern (see existing `ColonyEventSubscriber.register()`)
- `IColony.getDay()` — returns current colony day count, already used in `GetColonyAction` and `ColonyStatsHelper`
- `IColony.getPermissions().getPlayers()` — returns `Map<UUID, ColonyPlayer>` with `Rank` (look for `Rank.OWNER` / `isInitial()` to find founder)

## Verification

- `./gradlew :1.21.1-neoforge:compileJava` passes without errors
- New colony → `foundationEvent` is recorded and visible in `/talking_colonists events <colonyId>`
- Citizen prompt (via `/talking_colonists prompt`) contains colony age and founder
- Colony reaches day 100 → `ColonyStatsHelper` returns a milestone text mentioning it
- `enableFoundingReferences = false` suppresses all new content
- Colony deletion cleans up stored founding info (no memory leak)

## Key Patterns to Follow

- **Event subscriber pattern**: See `ColonyEventSubscriber.register()` — uses lambda syntax with `bus.subscribe(EventClass.class, event -> { ... })`
- **Event buffer pattern**: See `ColonyEventBuffer.recordRaid()` — timestamped event with description, LRU eviction
- **Milestone pattern**: See `ColonyStatsHelper.getColonyMilestoneText()` — collect eligible milestones, return `MiscUtil.pick()`
- **Prompt injection**: See `DefaultCitizenPromptProvider.addObservations()` — guard with config toggle, build StringBuilder, append to `prompt`
- **Prompt view extension**: See `CitizenPromptView` — append nullable fields at end of record, add Javadoc
