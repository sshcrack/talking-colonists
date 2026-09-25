# MineColonies Gameplay Mechanics Reference

This document is for agents designing Talking Colonists features. It describes **how the
game actually behaves** — rules, preconditions, triggers, timescales — not the addon API
surface (see `scripts/MINECOLONIES_DOCS.md` for that).

## Source and method

- Version researched: `com.ldtteam:minecolonies:1.1.1305-1.21.1-snapshot`, the dependency
  pinned for `1.21.1-neoforge` (the Stonecutter VCS-default target) in
  `stonecutter.properties.toml`. The `1.20.1-forge` target pins
  `1.20.1-1.1.1218-snapshot`; that jar was not decompiled for this document, but the two
  MineColonies builds share the same code generation lineage, so treat any claim below as
  "verified against 1.21.1-neoforge, very likely true on 1.20.1-forge too" unless a
  version-specific difference is called out.
- The MineColonies jar in the Gradle cache is **not obfuscated** — real class/method names
  are present — but Maven only publishes the compiled jar, not a `-sources.jar`. This
  document was produced by decompiling
  `~/.gradle/caches/modules-2/files-2.1/com.ldtteam/minecolonies/1.1.1305-1.21.1-snapshot/.../minecolonies-1.1.1305-1.21.1-snapshot.jar`
  with Vineflower 1.10.1 and reading the result directly. If you need to re-verify a claim,
  do the same and search by the class/method names cited in each section below (all
  citations use the real, undecompiled-obfuscated names, so `grep` will find them in the
  jar's class list too).
- Every citation below is `ClassName.methodName` (package `com.minecolonies.*` unless
  noted). Line numbers are not cited because they shift between decompiler runs and mod
  versions; class and method names are stable enough to `grep` for.
- Where a number is derived rather than read verbatim (e.g. "blocks per second"), it is
  marked **(estimate)** with the formula it comes from, so you can recompute it if the
  formula's inputs change.

---

## 1. Buildings

### 1.1 Hut placement vs. actually built

Placing a hut block (or scrolling/placing a schematic preview and confirming it) creates
an `IBuilding` at **level 0** with `isBuilt = false`. Nothing is "there" yet in gameplay
terms:

- No worker can be assigned (`IBuilding.canAssignCitizens()` default implementation
  requires `getBuildingLevel() > 0 && isBuilt()`).
- The building does not count towards housing, max-citizen capacity, or most happiness
  factors (all of those key off `getBuildingLevel()` or `isBuilt()`).
- A level-0 building still occupies its footprint and still needs a builder to construct
  it via a **BUILD** work order before it becomes level 1.

Only after a builder finishes the BUILD work order does `AbstractBuilding.onUpgradeComplete`
run, which sets `isBuilt = true`, claims chunks, removes construction tape, and recalculates
corners. Level 0 is a placeholder, not a "half-finished building" in the sense of a partial
build — nothing is actually constructed until a builder starts and finishes the work order.

**Pitfall**: don't treat "a hut block exists in the world" as "the building is functional."
Always check `building.getBuildingLevel() > 0 && building.isBuilt()` (or just
`isBuilt()`, since `isBuilt` only flips true on `onUpgradeComplete`).

Citations: `AbstractBuilding` (field `isBuilt`, default `false`; method
`requestUpgrade`), `IBuilding.canAssignCitizens` (default method).

### 1.2 Building levels, max levels, what a level unlocks

- Default max level for any building is **5** (`AbstractBuilding.getMaxBuildingLevel`
  returns `5` unless overridden). Most worker buildings keep this default.
- Overrides exist per building, e.g. `BuildingTownHall.getMaxBuildingLevel() == 5`,
  `BuildingGuardTower.getMaxBuildingLevel() == 5`. Check the specific
  `Building*.java`/decompiled class if you need an exact max for a given hut; don't assume
  5 for every type without checking.
- What a level unlocks is building-specific and lives in the module wiring, not a single
  formula. Concrete examples found:
  - **Housing capacity**: `CitizenManager.calculateMaxCitizens()` sums, for every built
    building (`getBuildingLevel() > 0`) that has both a `BED` module and a
    `WorkAtHomeBuildingModule` (i.e. a hut that both houses and employs, like the Builder's
    Hut), the module's `getModuleMax()` (worker/bed slots), and for pure housing
    (`LivingBuildingModule`, e.g. a house), `module.getModuleMax()` unless the module's
    hiring mode is `LOCKED`, in which case only the currently-assigned count counts. In
    short: **higher building level → more `getModuleMax()` slots → more beds/workers**, but
    the exact slots-per-level curve is defined per `BuildingEntry.ModuleProducer`
    registration (see `BuildingModules.java`), not a single global rule.
  - **Guard capacity**: see §5.2 — guard tower stays at 1 guard slot per guard type
    regardless of level; barracks/barracks tower scale 1:1 with building level.
  - **Food/health happiness**: `getHomeBuilding().getBuildingLevel()` feeds directly into
    the `housing` and `food` happiness factors (§4).
  - **Warehouse/storage**: `BuildingWareHouse.getMaxBuildingLevel()` and its own module
    wiring control storage slot counts (not derived here in detail — check
    `BuildingWareHouse.java` if a feature needs exact numbers).

**Pitfall**: there is no single "level → capacity" table in the code; capacity is a
property of the *module* a building has, configured per building type. Don't assume a
generic formula applies to every hut.

Citations: `AbstractBuilding.getMaxBuildingLevel`, `CitizenManager.calculateMaxCitizens`,
`BuildingModules` (module producer registrations), `BuildingGuardTower`, `BuildingTownHall`.

### 1.3 Town Hall level and other huts

There is **no hard-coded rule tying a hut's max level (or buildability) to the Town Hall's
level** in this version — searching for any `getTownHall().getBuildingLevel()` comparison
against another building's level turns up nothing except one unrelated use (citizen
respawn-interval tuning, `CitizenManager.onColonyTick`). What actually gates upgrades:

1. **Builder level** — see §2.2. A level-N upgrade needs a builder whose own Builder's Hut
   is level N or higher (with an exception for level 5 builders, who can build anything).
2. **Research** — `AbstractBuilding.requestUpgrade` checks
   `colony.getResearchManager().getResearchEffectIdFrom(getBuildingType().getBuildingBlock())`.
   If that hut type has an associated research effect and the effect's unlocked strength is
   below 1.0, the player is told "you have to unlock this building" and no work order is
   created; if the effect's strength is `<= current level`, they're told "unlock further to
   upgrade." Town Hall level indirectly matters here only because higher-level colonies
   generate more research points and unlock the University sooner — it is not a direct
   level check.
3. **Parent building** — a child building (e.g. a Barracks Tower, whose parent is the
   Barracks) cannot be upgraded past its parent's level: `requestUpgrade` looks up
   `getServerBuildingManager().getBuilding(getParent())` and compares levels before calling
   `requestWorkOrder`, otherwise the player gets `com.minecolonies.coremod.worker.noupgrade`.
   (Verified from the bytecode of `AbstractBuilding.requestUpgrade`.) The lang key
   `gui.townhall.toolow` ("Town Hall level too low!") exists but no class references it.
4. **Resource cost and time** scale with target level (bigger schematic, more resources,
   more blocks to place) — this is the main practical throttle, not a Town Hall gate.

**Pitfall**: don't design a feature assuming "Town Hall level 3 unlocks hut level 4" as a
hard rule. It doesn't exist in code; the actual gates are builder level and research.

Citations: `AbstractBuilding.requestUpgrade`, `CitizenManager.onColonyTick`.

### 1.4 Schematic/blueprint path

Every building tracks a **structure pack name** (`getStructurePack()`) and a **path**
inside that pack (`getStructurePath()` / `getBlueprintPath()`), e.g.
`schematics/colonist/builder1.blueprint`. When a work order is created,
`WorkOrderBuilding.create(WorkOrderType, IBuilding)`:

```
schemPath = building.getBlueprintPath().replace(".blueprint", "")
schemPath = schemPath.substring(0, schemPath.length() - 1) + targetSchematicLevel + ".blueprint"
```

i.e. it takes the current blueprint filename, strips the trailing level digit, and appends
the **target** level's digit. This means:

- Blueprint files are conventionally named `<hutname><level>.blueprint` (the level digit is
  the last character before the extension).
- `WorkOrderBuilding.create` **assumes** this naming convention. If a building's blueprint
  path doesn't end in a single digit before `.blueprint`, path construction breaks.
- The path is resolved against the colony's `StructurePacks` registry
  (`ColonyUtils.queueBlueprintLoad`) — the pack must actually be registered/loaded or the
  work order will never get a blueprint and can't progress.

**Pitfall for addon code**: never call `WorkOrderBuilding.create` for a building whose
`getBlueprintPath()` doesn't already follow the `<name><digit>.blueprint` convention — it
will silently substring-mangle the wrong character.

Citations: `WorkOrderBuilding.create`, `AbstractWorkOrder.loadBlueprint`,
`ColonyUtils.queueBlueprintLoad`.

---

## 2. Work orders

### 2.1 Types

`WorkOrderType` enum: `BUILD`, `UPGRADE`, `REPAIR`, `REMOVE`. There is also
`WorkOrderMiner`, `WorkOrderDecoration`, and `WorkOrderPlantationField` as separate work
order *classes* (not types) for the miner shaft, decorations, and plantation fields
respectively — those follow the same claim/priority/stage machinery but aren't triggered
via `IBuilding.requestUpgrade`.

| Type | Created by | Target level computed as |
|---|---|---|
| `BUILD` | `requestUpgrade` when `getBuildingLevel() == 0` | `1` |
| `UPGRADE` | `requestUpgrade` when `getBuildingLevel() > 0` and below max | `currentLevel + 1` |
| `REPAIR` | `requestRepair`, only if `getBuildingLevel() > 0` | `currentLevel` (rebuild in place, no level change) |
| `REMOVE` | `requestRemoval`, only if not already deconstructed | `0` |

Citation: `WorkOrderBuilding.create`, `AbstractBuilding.requestUpgrade/requestRepair/requestRemoval`.

### 2.2 Who can create a work order, and the preconditions

The **player-facing** path is `AbstractBuilding.requestUpgrade(Player, BlockPos builder)`,
called from the building GUI via `BuildRequestMessage`. Preconditions, in order:

1. **Research gate** (see §1.3) — must pass or the request is silently rejected with a
   chat message, no work order created.
2. **Level gate**: if already at `getMaxBuildingLevel()`, or the building has a parent
   (e.g. a Guard Tower attached to a Warehouse-style compound) whose own level is below
   its max and this building has already caught up to the parent's level, the player is
   told "no upgrade possible."
3. `requestWorkOrder(type, builder)` then checks, in order:
   - **Duplicate check**: if a `WorkOrderBuilding` already targets this building's
     location, silently return (only one active work order per building at a time).
   - **Deconstructable check** (REMOVE only): `canDeconstruct()` must be true.
   - **Builder-availability check** (non-REMOVE): either
     `canBeBuiltByBuilder(targetLevel)` is true for *this building itself* (only the
     Builder's Hut overrides this, to solve the bootstrapping problem — see below), **or**
     `workOrder.canBeResolved(colony, targetLevel)` is true, meaning *some* Builder's Hut in
     the colony has an assigned citizen and `getBuildingLevel() >= targetLevel`. If neither
     holds, the request is rejected with "a builder of at least level N is required" and no
     work order is created.
   - **Distance check**: `tooFarFromAnyBuilder` — for a normal building work order, true
     only if no staffed Builder's Hut is within `sqrt(10000) = 100` blocks (squared distance
     10000) of the target. If true *and* no explicit `builder` was passed, rejected.
   - **World-height checks**: target corners must be within `[minBuildHeight, maxBuildHeight]`.
   - **Explicit builder validation**: if a specific builder hut position was passed (not
     `BlockPos.ZERO`), that position must actually be an `AbstractBuildingStructureBuilder`
     (i.e. a Builder's Hut) whose level is `>= targetLevel` (unless the target building
     itself allows being built by lower-level builders); otherwise rejected.
4. If all checks pass, a `WorkOrderBuilding` is created and added to `WorkManager`, and if
   an explicit builder was supplied, the order is immediately claimed
   (`workOrder.setClaimedBy(builder)`).

**The Builder's Hut bootstrap special case**: `BuildingBuilder.canBeBuiltByBuilder(newLevel)`
returns `true` only when `newLevel == currentLevel + 1` — i.e. a Builder's Hut can always be
built/upgraded one level at a time by itself, without requiring an *already existing*
higher-level builder, solving the chicken-and-egg problem of "you need a builder to build
the first Builder's Hut."

**What happens with no builder, or a too-low-level builder**: the work order is either
never created (generic case — the game refuses up front, no "and it waits" behaviour) or,
if it *was* created earlier and later becomes unresolvable (e.g. the only qualifying
builder was fired), it simply sits unclaimed in `WorkManager` — `canBuild`/`canBeResolved`
are re-evaluated whenever a builder looks for work
(`WorkManager.getOrderedList`/`AbstractEntityAIStructure`), so a work order can wait
indefinitely with **no automatic escalation, timeout, or player notification** beyond the
one-time rejection message at creation time.

**Pitfall for addon code**: calling `IBuilding.requestUpgrade`/`requestRemoval` directly
from addon/mod code bypasses the `Action.MANAGE_HUTS` permission check that the normal
`BuildRequestMessage` network path enforces (see §8.3). If a feature lets a non-player actor
(e.g. voice command) trigger an upgrade, you are responsible for checking permissions
yourself.

Citations: `AbstractBuilding.requestUpgrade/requestRemoval/requestRepair/requestWorkOrder/canBeBuiltByBuilder`,
`BuildingBuilder.canBeBuiltByBuilder`, `WorkOrderBuilding.canBuild/canBuildIgnoringDistance/tooFarFromAnyBuilder`,
`AbstractWorkOrder.canBeResolved/tooFarFromAnyBuilder`, `BuildRequestMessage`,
`AbstractColonyServerMessage.permissionNeeded` (defaults to `Action.MANAGE_HUTS`).

### 2.3 Priority, claiming, and multiple builders

- Every work order has an integer `priority` (settable by the player via the building UI /
  `WorkOrderChangeMessage`). `WorkManager.getOrderedList(type, builderPos)` returns work
  orders **filtered to unclaimed-or-claimed-by-this-builder**, sorted by
  `Comparator.comparingInt(IWorkOrder::getPriority).reversed()` — highest priority first.
- `WorkManager.getUnassignedWorkOrder(type)` just returns the first unclaimed order of a
  type it finds (no priority sort) — used by simpler consumers.
- Claiming is exclusive: `AbstractWorkOrder.setClaimedBy` logs a warning if you try to claim
  an already-claimed order for a different builder (defensive check, not a hard failure).
- A builder can only work one order at a time (`BuildingBuilder.hasWorkOrder()` guards
  `setWorkOrder`); a second, higher-priority order will not preempt an order already in
  progress. It waits its turn in priority order once the current one clears.
- Multiple builders in one colony each pick from the ordered list independently; whichever
  builder AI polls first and satisfies `canBuildIgnoringDistance` claims it.

Citations: `WorkManager.getOrderedList/getUnassignedWorkOrder`, `AbstractWorkOrder.setClaimedBy`,
`BuildingBuilder.setWorkOrder/hasWorkOrder`.

### 2.4 Stages (`BuildingProgressStage`)

```java
enum BuildingProgressStage {
  CLEAR, BUILD_SOLID, CLEAR_WATER, CLEAR_NON_SOLIDS, DECORATE, SPAWN, REMOVE, REMOVE_WATER, WEAK_SOLID
}
```

A normal BUILD/UPGRADE work order progresses roughly `CLEAR → BUILD_SOLID → CLEAR_WATER →
CLEAR_NON_SOLIDS → DECORATE → SPAWN`, tracked via `structurePlacer`'s internal stage
machine and mirrored onto the work order (`AbstractWorkOrder.setStage`) for UI/progress
display. REMOVE orders use `REMOVE`/`REMOVE_WATER`. `WEAK_SOLID` is used for partial/weak
block placement passes. Stage transitions happen inside
`AbstractEntityAIStructure.doStructurePlacement` when the structure placer signals
`Result.FINISHED` for the current stage (`building.nextStage()` /
`structurePlacer.getB().nextStage()`).

Citation: `BuildingProgressStage`, `AbstractWorkOrder.getStage/setStage`,
`AbstractEntityAIStructure.doStructurePlacement/goToNextStage`.

### 2.5 Resource requests and progress measurement

- While clearing/placing, the builder AI calls
  `building.checkOrRequestBucket(building.getRequiredResources(), citizenData)` and, on a
  `Result.MISSING_ITEMS` result, either requests the missing items via the request system
  (§6) or returns `NEEDS_ITEM`/`LOAD_STRUCTURE` AI states to wait/retry.
- **Progress measurement**: there is no single "percent complete" field. The most reliable
  proxies are: (a) `WorkOrder.getStage()` (coarse phase), (b) the builder's
  `getProgressPos()` (current block iterator position within the blueprint — used to resume
  after logout/unload), and (c) counting placed-vs-total blocks by walking the blueprint if
  you need an actual percentage (not provided out of the box).
- Iteration is a **linear scan over the blueprint's block positions** (an
  `AbstractBlueprintIterator`), one "step" (attempted block placement or removal) per AI
  tick when not blocked on resources/mining/pathing.

Citations: `AbstractEntityAIStructure.doStructurePlacement`, `IBuilding.checkOrRequestBucket`
(via `getRequiredResources`), `AbstractWorkOrder` (`storeProgressPos`/iterator fields).

### 2.6 Realistic durations

**Block placement rate (estimate)**: after a successful placement, the AI sets a tick delay
before the next action:

```java
double decrease = 1.0 - researchEffects.getEffectStrength(BLOCK_PLACE_SPEED);
setDelay((int)((150 / (getPlaceSpeedLevel() / 2 + 10)) * decrease));
```

`getPlaceSpeedLevel()` is the builder's **primary skill level** (`EntityAIStructureBuilder.
getPlaceSpeedLevel`). `setDelay` sets a tick countdown before the AI's next block-placement
attempt. Plugging in numbers (ignoring the `BLOCK_PLACE_SPEED` research, i.e.
`decrease = 1.0`):

| Builder primary skill | Delay per block (ticks) | ≈ blocks/second |
|---|---|---|
| 0 | `150/10 = 15` | 1.3 |
| 20 | `150/20 = 7.5 → 7` | ~2.7 |
| 50 (high-skill citizen) | `150/35 ≈ 4.3 → 4` | ~4.6 |

**These numbers ignore travel time between blocks, pathing around obstacles, block-breaking
time for CLEAR stages, waiting on missing resources, and waiting on delivery from the
warehouse — all of which routinely dominate wall-clock build time.** A small hut (dozens of
blocks) might finish in a few in-game hours of active building; a multi-hundred-block
level-5 building realistically takes **multiple in-game days** once resource waits,
pathing, and the builder's other duties (eating, sleeping, idling) are accounted for. There
is no fixed "N days per level" constant in the code — do not promise players (or design
features around) a specific completion time.

**Colony day**: `IColony.getDay()` increments exactly once per vanilla day/night
transition — specifically when the world flips from night back to day
(`Colony.checkDayTime`, `!isDay && WorldUtil.isDayTime(world) → day++`). One colony day is
one vanilla Minecraft day (20 minutes real time at the default day length, or whatever the
world's `gameTime` day length actually is — this is **not** colony-specific pacing, it is
literally the vanilla day/night cycle).

Citations: `AbstractEntityAIStructure.doStructurePlacement/getPlaceSpeedLevel` (abstract,
overridden per job), `EntityAIStructureBuilder.getPlaceSpeedLevel/getBreakSpeedLevel`,
`AbstractEntityAIBasic.setDelay`, `Colony.checkDayTime/getDay`.

---

## 3. Citizens

### 3.1 Jobs and hiring (auto-hire)

- A citizen only has a job while assigned to a building's worker module. Auto-hire logic
  (`BuildingUtils.canAutoHire`) requires: the building `canAssignCitizens()` (level > 0,
  built), and either the building's own hiring mode is `AUTO`, or its mode is `DEFAULT` and
  the colony-wide Town Hall setting `AUTO_HIRING_MODE` is enabled, and the job isn't
  excluded by the colony's job-restriction tag settings.
- Auto-hire runs opportunistically on building/colony ticks (e.g.
  `GuardBuildingModule.onColonyTick` for guards) — it is not instantaneous; expect it within
  seconds to a few colony ticks after conditions become true, not the same game tick.
- A player can always hire manually via the building GUI regardless of auto-hire settings.

Citation: `BuildingUtils.canAutoHire`, `GuardBuildingModule.onColonyTick`.

### 3.2 Housing and beds

- `ICitizenData.getHomeBuilding()` — a citizen with no home building is "homeless" and takes
  the `homelessness` happiness hit (§4).
  `ColonyBuildingManager.getHouseWithSpareBed()` (or equivalent) is what the reproduction
  manager and citizen-assignment logic use to find available housing.
- Home assignment happens via a `LivingBuildingModule`/`BED` module on a residential
  building; assignment is separate from work assignment (a citizen's home and workplace can
  be different buildings).

Citation: `ReproductionManager.trySpawnChild` (uses `getHouseWithSpareBed()`),
`CitizenManager.calculateMaxCitizens` (BED + WorkAtHome module counting).

### 3.3 Children and growing up

New citizens are (usually) born as children, not adults — see §3.4 for the birth trigger.
Growing up (`EntityAICitizenChild.tryGrowUp`) works like this:

- Each child ticks its own "activity time" (`aiActiveTime`) roughly every 500 ticks (~25s),
  but only accrues 2000 units **if the colony's shared `additionalChildTime` budget has at
  least 2000 available** (`IColony.useAdditionalChildTime(2000)`). This budget refills at
  500/tick(-check) up to a **70000 cap**, but only while the colony actually has at least
  one child (`Colony.updateChildTime`) — so **multiple children compete for the same
  regenerating time budget**, and a colony with many children grows them up more slowly
  per-child than one with a single child.
- Once a child's own `aiActiveTime >= 4000`, each subsequent check has a
  `1 / (70 / growthModifier)` chance to trigger growth (`growthModifier` from the
  `GROWTH` research effect, default 1.0 → 1/70 per check), **or** growth is guaranteed once
  `aiActiveTime > 70000 / growthModifier`.
- **Estimate**: with one child in the colony and no growth research, the guaranteed-grow-up
  ceiling (`aiActiveTime` reaching 70000) requires roughly 35 successful 2000-unit
  accruals, i.e. ~35 checks — at ~25s apart and gated by the shared budget refilling at
  500/"tick" (itself throttled to run once per colony second-tick,
  `TickingTransition(..., 20)`), this works out to **very roughly half an hour to a couple
  of hours of real, loaded-world time**, with high variance from the 1/70 random roll
  potentially firing much earlier. Do not hardcode a specific "children grow up after X
  days" duration in a feature.

Citation: `EntityAICitizenChild.tryGrowUp`, `Colony.updateChildTime/useAdditionalChildTime`.

### 3.4 Spawning and colony growth

Two distinct growth mechanisms:

1. **Initial population** (`CitizenManager.onColonyTick`): while the Town Hall's `MOVE_IN`
   setting is enabled and the colony has fewer citizens than the server config's
   `initialCitizenAmount`, the colony spawns a new adult citizen at the Town Hall roughly
   every `1200 - (500 + 60 * townHallLevel)`-adjusted interval (a countdown timer reset to
   1200 ticks each time it fires; a higher Town Hall level shortens the effective countdown
   step). This is how a brand-new colony fills up to its starting roster without anyone
   being born.
2. **Births** (`ReproductionManager.trySpawnChild`, run from `onColonyTick`), once the
   initial roster is filled: requires
   - Town Hall `MOVE_IN` setting on,
   - current citizens `<` `getMaxCitizens()` (i.e. **a free housing/bed slot must exist** —
     see §3.2/§1.2),
   - current citizens `>= min(2, initialCitizenAmount)`,
   - a "bio parents" check (`checkForBioParents`, requires enough eligible adults),
   - a house with a spare bed (`getHouseWithSpareBed()`).
   The timer between birth attempts is `(6000 + random(0..12000)) * (currentCitizens /
   max(4, maxCitizens))` ticks — **this scales the interval up as the colony approaches its
   max population**, so births slow down as a colony fills up and stop entirely once full.
   Two existing citizens are opportunistically paired as parents (preferring existing
   partners, otherwise picking eligible unrelated adults, optionally from a nearby house).

**Pitfall**: population growth requires (a) free housing capacity and (b) enough eligible
adult citizens — a colony that is fully housed, or has only 1 adult, cannot grow via births
no matter how happy or well-fed it is.

Citations: `CitizenManager.onColonyTick`, `ReproductionManager.trySpawnChild/onColonyTick`.

### 3.5 Death and graves

On death (`EntityCitizen.die`):

- If the citizen dies **inside the colony's claimed area**, a grave block is placed
  (`IGraveManager.createCitizenGrave`) at/near the death location, and players are told its
  direction/distance from the colony center. If they die **outside** the colony (or are
  invisible), no grave is created and their inventory is simply dropped in the world.
- All colony members get a temporary colony-wide happiness penalty: an
  `ExpirationBasedHappinessModifier("death", weight 3.0, factor 0.0, duration 3 days)` is
  injected for every citizen **except** if the deceased was a guard (guards dying doesn't
  trigger the mourning penalty for the colony).
- Non-guard housemates/relatives additionally enter a "mourning" state
  (`CitizenManager.updateCitizenMourn`).
- **Death is permanent.** `CitizenManager.removeCivilian` removes the citizen for good; there
  is no revival mechanic. The **Undertaker** job (`JobUndertaker`,
  `EntityAIWorkUndertaker`) manages graves (digs/moves/tends them at a Graveyard building)
  — it does not resurrect citizens. Don't design a feature that assumes a dead citizen can
  come back.

Citations: `EntityCitizen.die`, `CitizenManager.removeCivilian/updateCitizenMourn`,
`JobUndertaker`, `EntityAIWorkUndertaker`.

---

## 4. Happiness

Happiness is computed per citizen (`CitizenHappinessHandler.getHappiness`) as a
weighted average of modifier "factors" (each factor is normally around 1.0 = neutral, `<
1.0` = unhappy contribution, `> 1.0` = happy contribution), scaled `0..10` and boosted by
the `HAPPINESS` research effect. **Only modifiers whose factor `!= 1.0` are counted at all**
(both numerator and denominator) — a modifier sitting exactly at 1.0 contributes nothing
either way.

```java
happiness = min(10 * (Σ factor*weight / Σ weight) * (1 + researchHappinessBonus), 10)
```

### 4.1 Every modifier, its weight, and what raises it

| id | weight | Type | What it measures | Formula | How to improve |
|---|---|---|---|---|---|
| `school` | 1.0 | Static | Is a child citizen actually enrolled as a Pupil? | `child ? (job==Pupil ? 2.0 : 0.0) : 1.0` | Assign every child citizen to the School as a Pupil job. Non-children are always neutral. |
| `security` | 4.0 | Static | Guard coverage relative to colony size | `getGuardFactor`: `min((guards+1) / ((workers+1) * 2/3), 2.0)` | Hire more guards relative to non-guard workers. Ratio target: **guards ≈ 2/3 of non-guard workers** gives the max factor (2.0). More guard *buildings* only matter insofar as they let you hire more guards (§5) — the factor only cares about the guard headcount, not tower vs. barracks. |
| `social` | 2.0 | Static | Fraction of citizens who are *not* unemployed, homeless, sick, or hungry | `getSocialModifier`: `(total - (unemployed+homeless+sick+hungry)) / total` | Fix the other four problems for as many citizens as possible; this modifier is a side-effect summary, not something to target directly. |
| `mysticalsite` | 1.0 | Static | Mystical Site building level | `getMysticalSiteFactor`: `max(1.0, mysticalSiteMaxLevel / 2.0)` | Build/upgrade a Mystical Site. Level 2+ gives >1.0; no Mystical Site gives exactly 1.0 (neutral, not penalized). |
| `food` | 3.0 | Static | Diet diversity and quality vs. home level | `getFoodFactor`: needs `homeBuildingLevel != 0` and a full food history; then `(diversityFactor + qualityFactor) / 2`, each capped at 5.0, `diversityFactor = min(5, distinctFoodsEaten/homeLevel)`, `qualityFactor = min(5, foodQuality/max(1, homeLevel-2))` | Feed citizens a *variety* of foods, and higher-quality/saturation foods, especially once home level > 2 (quality factor divisor grows with level, so late-game homes need better food to keep the same score). No home, or no food history yet, gives a neutral 1.0 (not a penalty) — this decouples "just placed" citizens from an unfair penalty. |
| `homelessness` | 3.0 | Time-based | Home building level | `getHousingFunction`: `homeBuilding==null ? 0.0 : homeLevel/3.0` (so level 3+ home = 1.0, i.e. "fully housed") | Assign a home; upgrade the home to level 3+. Below level 3, or homeless, the penalty **compounds**: after 7 consecutive bad days it's multiplied by 0.75, after 14 by 0.5 (i.e. gets *worse* the longer it persists, not just static). |
| `unemployment` | 2.0 | Time-based | Whether the citizen has a job, and its level | Children always 1.0 (neutral); no job → 0.5; job at a building level ≤ 3 → 1.0; job at a building level > 3 → 2.0 (bonus) | Give every adult a job; work towards higher-level workplaces for a happiness bonus, not just to remove the penalty. Same 7-/14-day compounding as homelessness. |
| `health` | 2.0 | Time-based | Currently sick? | `entity present && sick ? 0.5 : 1.0` | Cure sickness (Hospital/Healer). Same 7-/14-day compounding, but harsher (0.5×/0.1× vs 0.75×/0.5× for the others) — prolonged sickness tanks happiness fast. |
| `idleatjob` | 1.0 | Time-based | Is the citizen idle at their workplace (no task to do)? | `isIdleAtJob() ? 0.5 : 1.0` | Give idle workers something to do — usually means the building needs more resources/work orders/targets, or the worker is over-staffed relative to available tasks. Same 7-/14-day compounding (0.5×/0.1×). |
| `slepttonight` | 1.5 | Time-based (custom roll-over) | Did the citizen sleep? | Guards → 1.0 (exempt); everyone else → 0.5 baseline | This modifier's day-roll-over predicate is hardcoded `true` (always advances "days without sleeping" for non-guards, decoupled from the `factor < 1.0` default rule), then multiplied 2.0×/1.6×/1.0× at day thresholds 0/2/3 — i.e. it explicitly rewards guards (who "sleep on duty") and decays quickly for citizens who don't get to bed. Make sure citizens actually reach a bed at night (§7.2). |

Notes:

- "Time-based" modifiers use `TimeBasedHappinessModifier`: each colony day-end
  (`CitizenHappinessHandler.processDailyHappiness` → `dayEnd`), if the modifier's base
  factor is still `< 1.0` (still a problem), a `days` counter increments; the moment the
  problem is fixed, `days` resets to 0 (`reset()`). The thresholds above are `(daysBad,
  multiplier)` pairs applied on top of the base (already-bad) factor — so an unresolved
  problem gets *progressively* worse, not just persistently bad at the same level.
- There is no `research`/`static/no-happiness-yet-implemented` modifier for things like
  "distance to spawn" or "biome" in this version — only the 10 modifiers above exist
  (`HappinessConstants.VALID_HAPPINESS_MODIFIERS` is the authoritative list to check if this
  changes in a future MineColonies release).

**Pitfall**: `security` is driven purely by the **guard headcount ratio**, not by which
guard building type exists. A single Guard Tower with one hired guard contributes exactly
the same to `security` as a Barracks Tower slot with one hired guard — the type only
affects *how many guards you can hire in total* (§5.2), not the per-guard happiness
contribution.

Citations: `CitizenHappinessHandler` (constructor wiring + all `get*Factor`/`get*Modifier`
static helpers), `ModHappinessFactorTypeInitializer` (all function registrations),
`TimeBasedHappinessModifier.getFactor/dayEnd`, `HappinessRegistry`.

---

## 5. Guards and raids

### 5.1 Hiring guards

Guards are hired the same way as any other worker — assigned to a `GuardBuildingModule` on
a Guard Tower, Barracks Tower, Barracks, or Gate House. Auto-hire for guards additionally
supports **promoting a Squire/trainee**: `GuardBuildingModule.onColonyTick` first looks for
an existing `archerInTraining`/`knightInTraining` citizen with the highest relevant skill
level and promotes them if the `HIRE_TRAINEE` setting is on and the module isn't full,
before falling back to normal auto-hire of an unemployed citizen.

Citation: `GuardBuildingModule.onColonyTick`, `AbstractBuildingGuards`.

### 5.2 Guards per building type/level

Guard slot capacity per guard-type module (each guard building can host one or more guard
*type* modules — e.g. knight, ranger, druid — each with its own capacity function):

| Building | Slot capacity per guard type | Scales with level? |
|---|---|---|
| Guard Tower | **1** (`b -> 1`) | No — always exactly 1, regardless of tower level (higher levels give the guard more HP/damage/patrol range, not more guards) |
| Gate House | **2** (`b -> 2`) | No — flat 2 |
| Barracks Tower / Barracks | **`building.getBuildingLevel()`** (`ICommonBuilding::getBuildingLevel`) | **Yes** — a level 5 Barracks Tower can hold 5 guards of that type |

**Pitfall**: "build more guard towers" and "upgrade one barracks tower" are *not*
interchangeable ways to get more guards. A Guard Tower's level only makes its single guard
stronger; only Barracks-family buildings let level investment translate into *more* guard
headcount from one structure.

Citation: `BuildingModules` (`KNIGHT_TOWER_WORK`/`RANGER_TOWER_WORK`/`DRUID_TOWER_WORK`
size-limit `b -> 1`; `KNIGHT_GATE_WORK`/`RANGER_GATE_WORK` size-limit `b -> 2`;
`KNIGHT_BARRACKS_WORK`/`RANGER_BARRACKS_WORK`/`DRUID_BARRACKS_WORK` size-limit
`ICommonBuilding::getBuildingLevel`), `BuildingGuardTower.getMaxBuildingLevel`.

### 5.3 Raid triggers

Raids are decided **the night before** they happen (`RaidManager.determineRaidForNextDay`,
called on night-fall) and executed the following event window. Gate conditions
(`RaidManager.canRaid`):

- World difficulty is not Peaceful.
- Server config `enableColonyRaids` is on.
- The colony itself allows raids (`canHaveRaiderEvents()` — can be toggled off, e.g. by a
  colony style/permission/game rule).
- At least one "important" (subscribed/owning) player is currently online-ish for the
  colony (`getImportantColonyPlayers()` not empty) — **a colony with no players around
  cannot be raided**.

If those pass, `raidThisNight` decides probabilistically:

- Won't raid again if fewer nights have passed since the last raid than
  `minimumNumberOfNightsBetweenRaids` (server config default **10**) plus any
  `extraDaysToNextRaid` penalty.
- Beyond `averageNumberOfNightsBetweenRaids` (default **14**) + 2 nights since the last
  raid, a raid is *guaranteed*.
- In between, each eligible night has a `1 / (average - minimum)` chance (default `1/4` per
  eligible night, i.e. ~25%/night once past the minimum cooldown).

If a raid is scheduled, the actual raider **amount** and whether it happens at all is
further gated by `getColonyRaidLevel()`:

```
level = Σ_adult_citizens (5 + skillSum/100) + Σ_built_buildings (5 + buildingLevel²/5)
        + completedResearchCount * 3
level *= min(1, currentCitizens / maxCitizens)   // population-fill scaling
```

`calculateRaiderAmount(level)` derives a raider count from this; **if the computed amount is
`<= 0` or `level < 75`, the raid is aborted as `TOO_SMALL`** — a very young/small colony
(low citizen count, low building levels, low skills) effectively cannot be raided yet even
if the night-roll said "raid tonight."

**Pitfall**: "N in-game days until a raid" is not a fixed number — it's a probability
distribution with a hard floor (10 nights) and a soft ceiling (~16 nights), further gated by
a colony-strength threshold that a brand-new colony won't clear. Don't promise players a
specific raid schedule.

Citations: `RaidManager.canRaid/determineRaidForNextDay/raidThisNight/getColonyRaidLevel/raiderEvent`,
`ServerConfiguration` (`averageNumberOfNightsBetweenRaids` = 14,
`minimumNumberOfNightsBetweenRaids` = 10, `maxRaiders` default 80).

---

## 6. Requests and deliveries

MineColonies routes almost all worker resource needs through a generic **request system**
rather than direct inventory access:

- A worker (or building) creates an `IRequest` for some `IDeliverable` (an item, a tool, a
  specific tag of items, etc.) via the colony's `IRequestManager`.
- The request is handed to a chain of **resolvers**, tried roughly in specificity order:
  a private/public **crafting resolver** (can another citizen craft it?), a **building
  resolver** (does the requesting building itself have it?), and finally the
  **`WarehouseRequestResolver`** (does the Warehouse have it in storage?). There's also a
  `PickupRequestResolver`/`DeliveryRequestResolver` pair specifically for courier
  (Deliveryman) tasks, and a `StationRequestResolver` for rail/cart-station-adjacent
  buildings.
- If a resolver can fulfil the request, a **Courier/Deliveryman** citizen is dispatched to
  physically move the item from its source (warehouse, another building, a crafter) to the
  requester. This is a real, in-world entity walking the path — it is not instantaneous.
- **"Waiting for resolver" in practice** means: no assigned resolver currently has the item
  available (warehouse is empty of it, no one can craft it, no building has spare stock).
  The request sits open; it does **not** poll external item generation on its own — someone
  (a player, a producer worker) has to actually produce/deposit the item before a resolver
  can satisfy the request. A stuck "waiting for resolver" request is a signal that supply is
  missing, not a transient delay.

**Pitfall for addon/feature code**: don't assume a resolved request means the item is
instantly in the worker's hands — a Courier still has to walk it over, which is subject to
the same pathing/travel-time realities as builder work.

Citations: `com.minecolonies.core.colony.requestsystem.resolvers.*`
(`WarehouseRequestResolver`, `BuildingRequestResolver`, `DeliveryRequestResolver`,
`PickupRequestResolver`, `PrivateWorkerCraftingRequestResolver`,
`PublicWorkerCraftingRequestResolver`, `StationRequestResolver`), `IRequestManager`.

---

## 7. Colony day/time

### 7.1 What counts as a colony day

`IColony.getDay()` increments by exactly 1 each time the world transitions from night back
to day (`Colony.checkDayTime`). This is tied to vanilla's day/night cycle
(`WorldUtil.isDayTime`), not a colony-specific clock — one colony day is one vanilla
Minecraft day.

### 7.2 Night behaviour

- On nightfall (`Colony.checkDayTime` detecting day→night), the colony fires
  `eventManager.onNightFall()` and `raidManager.onNightFall()` (which is when tomorrow's
  raid gets decided, see §5.3), and marks `citizenManager.updateCitizenSleep(false)`.
- `CitizenManager.onCitizenSleep()` — once **every** non-guard citizen is asleep (guards are
  exempt and patrol through the night), the colony is considered "asleep" and announces it
  in chat once. This ties directly into the `slepttonight` happiness modifier (§4) — a
  citizen who can't reach a bed (no home, bed occupied, pathing blocked, still working a
  night-shift task) doesn't get credited as having slept and accrues the decaying
  `slepttonight` penalty.
- On day-break, `citizenManager.onWakeUp()` handles mourning-state clearing/starting for
  citizens who lost a relative, and each child's growth AI resumes ticking (§3.3).

**Pitfall**: "go home at night" is not a forced, guaranteed teleport — it's emergent from
citizens' individual AI deciding to path to their assigned bed. A citizen with a bad path to
their home building, or no home at all, will not reliably sleep, and this shows up as a
persistent happiness problem, not a one-off warning.

Citations: `Colony.checkDayTime`, `CitizenManager.updateCitizenSleep/onCitizenSleep/onWakeUp`.

---

## 8. Common pitfalls for addon code

### 8.1 Server thread

Colony/building/citizen state (`IColony`, `IBuilding`, `ICitizenData`, work orders, request
manager) is server-authoritative and mutated on the server thread inside colony ticks. Read
or mutate it from the server thread; do not touch it from network/client callback threads or
arbitrary async contexts without hopping onto the server thread first (the same rule that
applies to any Minecraft/NeoForge world state).

### 8.2 Unloaded colonies and entities

- A colony can exist (and tick a reduced "unloaded" state machine —
  `Colony.worldTickUnloaded`, distinct from the normal `worldTickSlow`) even when its chunks
  aren't loaded. Building/citizen queries can return stale or partially-updated data for an
  unloaded colony; getters like `getTileEntity()` explicitly check
  `WorldUtil.isBlockLoaded(world, pos)` before trusting the live block entity and otherwise
  fall back to cached building state.
- A citizen's actual `Entity`/`AbstractEntityCitizen` may not exist even though its
  `ICitizenData` does — citizens despawn (are represented only as data) when unloaded or
  resting. Always null-check `citizenData.getEntity()` (an `Optional`) before touching the
  live entity; many internal factor calculations (e.g. `health` happiness) do exactly this
  (`data.getEntity().isPresent() ? ... : 1.0`) rather than assume the entity exists.

### 8.3 Permissions (`isColonyMember`, `hasPermission`)

- `IPermissions.isColonyMember(Player)` and `hasPermission(Player, Action)` are the checks
  the game itself uses before letting a player trigger colony-mutating actions. The generic
  network message base class (`AbstractColonyServerMessage.onExecute`) defaults every
  colony message's required permission to **`Action.MANAGE_HUTS`** unless a subclass
  overrides `permissionNeeded()`, and separately supports an `ownerOnly()` flag for
  owner-restricted actions (checked via `colony.getPermissions().getOwner()`).
- **Calling `IBuilding`/`IColony` mutator methods directly from addon code does not go
  through this check** — you get the raw API surface, permission-agnostic. If a feature
  lets an untrusted actor (a voice command from an arbitrary player, for instance) trigger
  something a normal player would need `MANAGE_HUTS`/ownership for, replicate the permission
  check yourself before calling the mutator.

### 8.4 `WorkOrderBuilding.create` needs a valid blueprint path

Already covered in §1.4 — re-flagging here because it's a common addon mistake: if you ever
construct or trigger a `WorkOrderBuilding` for a building whose `getBlueprintPath()` isn't
`<name><digit>.blueprint`, the target-level substitution logic silently produces a garbage
path and the work order will never resolve a blueprint.

### 8.5 Other gotchas found while reading the source

- **`AbstractWorkOrder.getBoundingBox()` lazily loads the blueprint** the first time it's
  called if the box hasn't been computed yet — calling it from a hot path (e.g. every tick)
  can trigger repeated async blueprint loads if the load keeps getting cleared. Cache the
  result if you need it often.
- **`requestWorkOrder` silently no-ops** (returns without any message) if a work order
  already exists for that building's location — don't assume "I called requestUpgrade and
  nothing happened" means an error; it likely means one's already pending.
- **`canBeResolved`/`tooFarFromAnyBuilder` are re-evaluated dynamically**, not cached at
  work-order-creation time — a work order that couldn't be claimed can start being claimed
  later purely because colony state changed (a new builder was hired, an old one leveled
  up), with no explicit re-trigger needed from your code.
- **Happiness modifiers marked "static" can still change every tick** — `StaticHappinessModifier`
  just means "not time-decayed," not "constant"; its factor is recomputed live from a
  `DynamicHappinessSupplier` each time `getHappiness()` is queried (and cached only until
  the handler is marked dirty).

---

## 9. Cheat sheet: "if a feature wants X, the realistic way is Y, and it takes roughly Z"

| Feature wants… | Realistic in-game trigger (Y) | Rough timescale (Z) |
|---|---|---|
| A new hut to exist | Place the hut block/schematic (creates level 0), **then** a Builder's Hut with a hired, sufficiently-leveled builder must complete a BUILD work order | Level 0 is instant; BUILD completion is minutes-to-hours of active building time depending on size, resources, and builder skill (§2.6) — never assume it's instant or guaranteed on any deadline |
| A building upgraded | An UPGRADE work order, same builder-level/research/distance gates as BUILD (§2.2), claimed and completed by a builder | Scales up with target level (bigger schematic, more resources); no fixed day count exists in code |
| More/better guards | Hire into existing guard slots (auto-hire or manual); to get *more* slots, build/upgrade Barracks or Barracks Tower (not Guard Tower — its slot count is fixed at 1, §5.2) | Hiring: next auto-hire tick after conditions are met (seconds). More slots: requires an UPGRADE work order like any other building |
| Higher `security` happiness | Increase the guard-to-worker ratio towards guards ≈ 2/3 of non-guard workers (§4.1) | Immediate once the ratio changes (happiness recalculates on next query/cache-invalidation), but *reaching* that ratio takes as long as hiring/building does |
| A raid to happen (for testing) | Wait out the night-by-night probability (§5.3), or use dev/admin commands to force one; natural raids also need `colonyRaidLevel >= 75` | At least 10 nights since the last raid, ~25%/night after that, guaranteed by night 16; blocked entirely for very young/weak colonies |
| Colony population to grow | Free bed/housing capacity + at least 2 adult citizens (past the initial-citizen-amount phase); reproduction timer scales with how full the colony already is (§3.4) | Initial roster fills every ~1200-ticks-minus-townhall-bonus (~1 minute, faster with a higher Town Hall); births after that are on the order of tens of seconds to a few minutes per attempt, gated by free housing |
| A child to grow up | Nothing to trigger — it's autonomous, shared time-budget based (§3.3) | Highly variable; roughly half an hour to a couple of hours of loaded, active time per child **(estimate)**, faster with the `GROWTH` research effect, slower with many children sharing the budget |
| A citizen to be fixed after death | Nothing — death is permanent (§3.5); design around prevention (better guards/health/defenses) or acceptance (new citizen via births/initial spawn), not revival | N/A — there is no revival path in the base game |
| A resource-dependent task to finish | Ensure the item is actually producible/stored somewhere a resolver can reach (crafter, building stock, or Warehouse) — a "waiting for resolver" state will not resolve itself without supply (§6) | Once supply exists: one Courier trip (real travel time) plus normal AI tick cadence |
| Citizens to sleep through a raid/night safely | Ensure every non-guard citizen has a reachable, unoccupied bed in an assigned home building (§7.2) | Nightly, but only if pathing to the bed actually succeeds — a blocked/missing home shows up as a persistent `slepttonight`/`homelessness` happiness problem, not a one-off event |

---

## Appendix: class/method index

Quick lookup of every class cited above, for `grep`-ing the decompiled jar or reading it
fresh from `com.ldtteam:minecolonies:<version>`:

- `com.minecolonies.core.colony.Colony` — `checkDayTime`, `getDay`, `updateChildTime`,
  `useAdditionalChildTime`, `updateHasChilds`, `onWorldTick`
- `com.minecolonies.core.colony.buildings.AbstractBuilding` — `requestUpgrade`,
  `requestRemoval`, `requestRepair`, `requestWorkOrder`, `canBeBuiltByBuilder`,
  `getMaxBuildingLevel`, `onUpgradeComplete`, `isBuilt`
- `com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder` —
  `canBeBuiltByBuilder`, `setWorkOrder`, `hasWorkOrder`
- `com.minecolonies.core.colony.buildings.workerbuildings.BuildingGuardTower`,
  `BuildingBarracksTower`, `BuildingBarracks`, `BuildingTownHall`, `BuildingWareHouse` —
  per-type `getMaxBuildingLevel`/`getClaimRadius` overrides
- `com.minecolonies.core.colony.workorders.WorkOrderBuilding` — `create`, `canBuild`,
  `canBuildIgnoringDistance`, `tooFarFromAnyBuilder`
- `com.minecolonies.core.colony.workorders.AbstractWorkOrder` — `canBeResolved`,
  `getStage`/`setStage`, `loadBlueprint`, `getBoundingBox`
- `com.minecolonies.core.colony.workorders.WorkManager` — `getOrderedList`,
  `getUnassignedWorkOrder`, `getWorkOrdersOfType`
- `com.minecolonies.core.entity.ai.workers.AbstractEntityAIStructure` —
  `doStructurePlacement`, `getPlaceSpeedLevel` (abstract)
- `com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder` —
  `getPlaceSpeedLevel`, `getBreakSpeedLevel`
- `com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic` — `setDelay`
- `com.minecolonies.core.entity.citizen.citizenhandlers.CitizenHappinessHandler` — all
  `get*Factor`/`get*Modifier` statics, constructor (modifier wiring/weights)
- `com.minecolonies.apiimp.initializer.ModHappinessFactorTypeInitializer` — the actual
  per-modifier factor functions
- `com.minecolonies.api.entity.citizen.happiness.TimeBasedHappinessModifier` — `getFactor`,
  `dayEnd`, `reset`
- `com.minecolonies.core.colony.managers.CitizenManager` — `onColonyTick`,
  `calculateMaxCitizens`, `updateCitizenSleep`, `onCitizenSleep`, `onWakeUp`,
  `removeCivilian`, `updateCitizenMourn`
- `com.minecolonies.core.colony.managers.ReproductionManager` — `trySpawnChild`,
  `onColonyTick`
- `com.minecolonies.core.entity.ai.minimal.EntityAICitizenChild` — `tryGrowUp`
- `com.minecolonies.core.entity.citizen.EntityCitizen` — `die`
- `com.minecolonies.core.colony.jobs.JobUndertaker`,
  `com.minecolonies.core.entity.ai.workers.service.EntityAIWorkUndertaker`
- `com.minecolonies.core.colony.buildings.modules.GuardBuildingModule` — `onColonyTick`,
  `isFull`
- `com.minecolonies.core.colony.buildings.modules.BuildingModules` — guard module
  size-limit registrations (tower/gate/barracks)
- `com.minecolonies.core.colony.events.raid.RaidManager` — `canRaid`,
  `determineRaidForNextDay`, `raidThisNight`, `getColonyRaidLevel`, `raiderEvent`
- `com.minecolonies.api.configuration.ServerConfiguration` — raid/citizen-cap config
  defaults
- `com.minecolonies.core.colony.requestsystem.resolvers.*` — the resolver chain
- `com.minecolonies.core.network.messages.server.AbstractColonyServerMessage`,
  `AbstractBuildingServerMessage`, `BuildRequestMessage` — permission checks and the
  player-facing request path
- `com.minecolonies.core.util.BuildingUtils` — `canAutoHire`
