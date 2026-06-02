# MineColonies API & Inner Workings

This document documents the MineColonies mod codebase for AI agents working with the mod. It covers architecture, key APIs, data flow, and integration points, covering both the **NeoForge 1.21.1** version (1.1.1305) and the **Forge 1.20.1** version (1.1.1218). See §20 for differences between the two.

---

## 1. Overview

MineColonies (`modId: minecolonies`) is a colony simulation mod. Players found colonies (via the Town Hall block), which spawn and manage NPC citizens with jobs, skills, happiness, and needs. Citizens work in player-placed huts that upgrade through levels (1-5). Raiders periodically attack. A sophisticated **Request System** handles item/resource logistics.

- **Mod ID**: `minecolonies`
- **Main class**: `com.minecolonies.core.MineColonies`
- **API root**: `com.minecolonies.api.IMinecoloniesAPI` (singleton via `IMinecoloniesAPI.getInstance()`)
- **Platform**: NeoForge 1.21.1 (also supports Forge 1.20.1 — differences in §20)

---

## 2. Architecture

### 2.1 Version Branches

Two active releases:
- **NeoForge 1.21.1** (`1.1.1305`‑snapshot) — current, more registrations, data components, claim system
- **Forge 1.20.1** (`1.1.1218`‑snapshot) — legacy, capabilities‑based, fewer features

The API surface is largely identical; all major interfaces (`IColony`, `ICitizenData`, `IBuilding`, `IJob`, `IRequestManager`, etc.) exist in both. Differences are documented in §20.

### 2.2 Core Data Flow

```
World → Colony (per dimension, centered on Town Hall)
  ├── CitizenManager → ICitizenData[]{ citizens }
  │     └── ICitizenData → IJob → ITickingStateAI (state machine)
  ├── BuildingManager → IBuilding[]{ huts }
  │     └── IBuilding → IBuildingModule[]{ modules }
  ├── WorkManager → work orders
  ├── RequestManager → request/resolver system
  ├── RaidManager → raid/event system
  ├── ResearchManager → research effects
  └── QuestManager → quests
```

### 2.3 Side Split (Server/Client)

- **Server**: `Colony` class (implements `IColony`) — full simulation logic
- **Client**: `ColonyView` class (implements `IColonyView` which extends `IColony`) — subset of data synced via network packets

The `IColonyManager.getIColony()` method is **side-neutral**: returns `Colony` on server, `ColonyView` on client.

### 2.4 Registry System

Custom registries for buildings, jobs, guard types, interaction handlers, research, quests, etc. All accessible via `IMinecoloniesAPI`:

| Registry | API Method | Entry Type | Return Type |
|----------|-----------|------------|-------------|
| Building | `getBuildingRegistry()` | `BuildingEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Job | `getJobRegistry()` | `JobEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Guard Type | `getGuardTypeRegistry()` | `GuardType` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Interaction Handler | `getInteractionResponseHandlerRegistry()` | `InteractionResponseHandlerEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Research Effects | `getResearchEffectRegistry()` | `ResearchEffectEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Research Requirements | `getResearchRequirementRegistry()` | `ResearchRequirementEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Research Costs | `getResearchCostRegistry()` | `ResearchCostEntry` | Forge only (see §20.14) |
| Recipe Types | `getRecipeTypeRegistry()` | `RecipeTypeEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Crafting Types | `getCraftingTypeRegistry()` | `CraftingType` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Quest Objectives/Triggers/Rewards | various quest registries | Various | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Happiness Factors | `getHappinessTypeRegistry()` | `HappinessFactorTypeEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |
| Equipment Types | `getEquipmentTypeRegistry()` | `EquipmentTypeEntry` | Neo: `Registry<>` / Forge: `IForgeRegistry<>` |

---

## 3. Colony (`IColony`)

Path: `com.minecolonies.api.colony.IColony`

### 3.1 Core Properties

| Property | Type | Description |
|----------|------|-------------|
| `getID()` | `int` | Unique colony ID |
| `getName()` / `setName()` | `String` | Colony name |
| `getCenter()` | `BlockPos` | Center position (Town Hall location) |
| `getDimension()` | `ResourceKey<Level>` | Dimension |
| `isRemote()` | `boolean` | True on client (ColonyView) |
| `getWorld()` | `Level` | World reference |
| `getOverallHappiness()` | `double` | Aggregate happiness (0.0-10.0) |
| `getState()` | `ColonyState` | INACTIVE, ACTIVE, or UNLOADED |

### 3.2 Sub-Managers

Accessed from `IColony`:

| Method | Returns | Purpose |
|--------|---------|---------|
| `getCitizenManager()` | `ICitizenManager` | Citizen CRUD + iteration |
| `getVisitorManager()` | `IVisitorManager` | Visitor NPCs |
| `getServerBuildingManager()` | `IRegisteredStructureManager` | Building CRUD |
| `getWorkManager()` | `IWorkManager` | Build/upgrade/repair work orders |
| `getRequestManager()` | `IRequestManager` | Request system |
| `getRaiderManager()` | `IRaiderManager` | Raids |
| `getEventManager()` | `IEventManager` | Colony events (raids, etc.) |
| `getResearchManager()` | `IResearchManager` | Research tree + effects |
| `getQuestManager()` | `IQuestManager` | Quest system |
| `getReproductionManager()` | `IReproductionManager` | Citizen breeding |
| `getGraveManager()` | `IGraveManager` | Citizen graves |
| `getAnimalManager()` | `IAnimalManager` | Animal tracking |
| `getPackageManager()` | `IColonyPackageManager` | Network sync |
| `getTravellingManager()` | `ITravellingManager` | Citizen travel |
| `getConnectionManager()` | `IColonyConnectionManager` | Inter-colony connections/diplomacy |
| `getPermissions()` | `IPermissions` | Permission system |

### 3.3 Colony State Machine

Colonies have a `ITickRateStateMachine<ColonyState>` with states:
- `INACTIVE` — not ticking
- `ACTIVE` — fully ticking
- `UNLOADED` — chunks not loaded

### 3.4 Serialization

Methods differ between versions:
- **NeoForge**: `write(CompoundTag, HolderLookup.Provider)` / `read(CompoundTag, HolderLookup.Provider)`
- **Forge**: `write(CompoundTag)` / `read(CompoundTag)` — no `HolderLookup` parameter

### 3.4 Key Managers Detail

#### IRaiderManager
- `canHaveRaiderEvents()` / `setCanHaveRaiderEvents(boolean)`
- `willRaidTonight()` — prediction
- `setRaidNextNight(RaidSettings)` — force next raid
- `raiderEvent(RaidSettings)` → `RaidSpawnResult` — trigger raid
- `calculateSpawnLocation()` → `BlockPos`
- `calculateRaiderAmount(int raidLevel)` → count
- `isRaided()` — currently under raid
- `getNightsSinceLastRaid()` / `setNightsSinceLastRaid(int)`
- `areSpiesEnabled()` / `setSpiesEnabled(boolean)`

Raid spawn results: `SUCCESS`, `TOO_SMALL`, `CANNOT_RAID`, `NO_SPAWN_POINT`, `ERROR`

---

## 4. IColonyManager — Global Entry Point

Path: `com.minecolonies.api.colony.IColonyManager`

Singleton access:
```java
IColonyManager.getInstance()
// or via IMinecoloniesAPI.getInstance().getColonyManager()
```

### 4.1 Key Methods

```java
IColony createColony(ServerLevel w, BlockPos pos, Player player, String colonyName, String pack);
void deleteColonyByWorld(int id, boolean canDestroy, ServerLevel world);

// Lookup by world or dimension
IColony getColonyByWorld(int id, Level world);
IColony getColonyByDimension(int id, ResourceKey<Level> dimension);

// Position-based (auto-detects containing colony)
IColony getColonyByPosFromWorld(Level w, BlockPos pos);
IColony getColonyByPosFromDim(ResourceKey<Level> dim, BlockPos pos);

// Side-neutral (Colony on server, ColonyView on client)
IColony getIColony(Level w, BlockPos pos);
IColony getClosestIColony(Level w, BlockPos pos);

// Owner-based
IColony getIColonyByOwner(Level w, Player owner);
IColony getIColonyByOwner(Level w, UUID owner);

// Buildings
IBuilding getBuilding(Level w, BlockPos pos);
IBuildingView getBuildingView(ResourceKey<Level> dimension, BlockPos pos);

// Listing
List<IColony> getColonies(Level w);
List<IColony> getAllColonies();
List<IColony> getColoniesAbandonedSince(int hoursSinceLastContact);

// Claims
Map<ChunkPos, IChunkClaimData> getClaimData(ResourceKey<Level> dimension);
IChunkClaimData getClaimData(ResourceKey<Level> dimension, ChunkPos pos);

// Managers
ICompatibilityManager getCompatibilityManager();
IRecipeManager getRecipeManager();
```

---

## 5. Citizen System

### 5.1 ICitizenData

Path: `com.minecolonies.api.colony.ICitizenData`
Extends: `ICivilianData`, `IQuestGiver`, `IQuestParticipant`

The primary data holder for each citizen. Accessed via `ICitizenManager`.

#### Core Properties

| Method | Type | Description |
|--------|------|-------------|
| `getId()` | `int` | Citizen ID (unique within colony) |
| `getEntity()` | `Optional<AbstractEntityCitizen>` | The actual entity if loaded |
| `getColony()` | `IColony` | Parent colony |
| `getName()` | `String` | Citizen name |
| `getHomeBuilding()` | `@Nullable IBuilding` | Residence |
| `getWorkBuilding()` | `@Nullable IBuilding` | Workplace |
| `getJob()` | `IJob<?>` | Current job |
| `getJob(Class<J>)` | `<J> @Nullable` | Typed job |
| `getSaturation()` / `setSaturation(double)` | `double` | Food level (0-20) |
| `getCitizenSkillHandler()` | `ICitizenSkillHandler` | Skill/XP system |
| `getCitizenHappinessHandler()` | `ICitizenHappinessHandler` | Happiness modifiers |
| `getCitizenDiseaseHandler()` | `ICitizenDiseaseHandler` | Disease state |
| `getCitizenFoodHandler()` | `ICitizenFoodHandler` | Food tracking |
| `getCitizenMournHandler()` | `ICitizenMournHandler` | Mourning tracking |
| `getJobStatus()` | `JobStatus` | IDLE, WORKING, or STUCK |
| `isAsleep()` / `setAsleep(boolean)` | `boolean` | Sleep state |
| `getBedPos()` / `setBedPos(BlockPos)` | `BlockPos` | Bed location |
| `isChild()` / `setIsChild(boolean)` | `boolean` | Child flag |
| `getStatus()` / `setVisibleStatus(VisibleCitizenStatus)` | `VisibleCitizenStatus` | Head icon |
| `getPartner()` | `@Nullable ICitizenData` | Marriage partner |
| `getChildren()` | `List<Integer>` | Children citizen IDs |
| `getSiblings()` | `List<Integer>` | Sibling IDs |
| `getParents()` | `Tuple<String, String>` | Parent names |
| `isRelatedTo(ICitizenData)` | `boolean` | Kinship check |
| `doesLiveWith(ICitizenData)` | `boolean` | Cohabitation check |
| `setIdleDays(int)` | — | Force idle for N days |
| `onQuestCompletion(ResourceLocation)` | — | Quest callback |
| `getRandom()` | `Random` | Per-citizen RNG |

#### JobStatus Enum
- `IDLE` — no work to do (or unemployed)
- `WORKING` — currently working
- `STUCK` — has work but is blocked (missing items/equipment)

### 5.2 Skills

Path: `com.minecolonies.api.entity.citizen.Skill`

| Skill | Complimentary | Adverse |
|-------|--------------|---------|
| Athletics | Strength | Dexterity |
| Dexterity | Agility | Athletics |
| Strength | Athletics | Agility |
| Agility | Dexterity | Strength |
| Stamina | Knowledge | Mana |
| Mana | — | Stamina |
| Knowledge | Stamina | — |

Each skill has a level, XP, and a level cap. Managed via `ICitizenSkillHandler`:
- `getLevel(Skill)` → `int`
- `addXpToSkill(Skill, double, ICitizenData)`
- `levelUp(ICitizenData)` — triggers on-level-up effects
- `getTotalXP()` → `double`
- `tryLevelUpIntelligence(Random, double, ICitizenData)` → `boolean`

### 5.3 Happiness

Managed via `ICitizenHappinessHandler`:
- `addModifier(IHappinessModifier)`
- `resetModifier(String name)`
- `getHappiness(IColony, ICitizenData)` → `double` (0.0-10.0)
- `processDailyHappiness(ICitizenData)`

Happiness uses a registry of `HappinessFactorTypeEntry` and `HappinessFunctionEntry`.

### 5.4 VisibleCitizenStatus (Head Icons)

Path: `com.minecolonies.api.entity.citizen.VisibleCitizenStatus`

Pre-defined statuses: `EAT`, `HOUSE`, `RAIDED`, `MOURNING`, `BAD_WEATHER`, `SLEEP`, `SICK`, `WORKING`

Set via `ICitizenData.setVisibleStatus(VisibleCitizenStatus)` — shows icon over citizen head.

### 5.5 Entity Hierarchy

```
Entity → PathfinderMob → AbstractFastMinecoloniesEntity → AbstractCivilianEntity → AbstractEntityCitizen
```

`AbstractEntityCitizen` (path: `com.minecolonies.api.entity.citizen.AbstractEntityCitizen`):
- Implements `Npc`, `MenuProvider`, `IItemHandlerCapProvider`
- Has an `ITickRateStateMachine<IState>` for entity state control (default tickrate: 5)
- Synced data: `DATA_LEVEL`, `DATA_TEXTURE`, `DATA_IS_FEMALE`, `DATA_COLONY_ID`, `DATA_CITIZEN_ID`, `DATA_MODEL`, `DATA_IS_ASLEEP`, `DATA_IS_CHILD`, `DATA_BED_POS`, `DATA_STYLE`, `DATA_JOB`
- Uses custom `AbstractAdvancedPathNavigate` for pathfinding
- Collision detection, equipment syncing, sound queuing

Entity data synced to client via `ICitizenDataView`.

### 5.6 Citizen Colony Handler

Path: `com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenColonyHandler`
- `getWorkBuilding()` / `getHomeBuilding()`
- `registerWithColony(int colonyID, int citizenID)`
- `updateColonyClient()`
- `getColony()` / `getColonyId()`

---

## 6. Job System

### 6.1 IJob

Path: `com.minecolonies.api.colony.jobs.IJob<AI extends ITickingStateAI>`

Each citizen has exactly one job. Jobs are registered via `JobEntry` in the job registry.

```java
JobEntry getJobRegistryEntry();
ResourceLocation getModel();          // model type lookup
IColony getColony();
ICitizenData getCitizen();
AI generateAI();                      // creates the worker AI
AI getWorkerAI();                     // gets current AI
void createAI();
String getNameTagDescription();       // status display text
boolean canAIBeInterrupted();
int getActionsDone();
void incrementActionsDone();
void clearActionsDone();
boolean isIdling();
boolean allowsAvoidance();
```

### 6.2 JobEntry Registry

Path: `com.minecolonies.api.colony.jobs.registry.JobEntry`

Builder pattern:
```java
new JobEntry.Builder()
    .setJobProducer(citizenData -> new JobFlorist(citizenData))
    .setJobViewProducer(() -> JobView::new)
    .setRegistryName(ResourceLocation.parse("minecolonies:job.florist"))
    .createJobEntry();
```

### 6.3 AI State Machine

Each job produces an `ITickingStateAI` that contains an `ITickRateStateMachine<IAIState>`.

The state machine system:
- **IStateMachine<T, S>** — base interface with `tick()`, `addTransition()`, `removeTransition()`, `getState()`, `reset()`
- **ITickRateStateMachine<S>** — tickrate-aware state machine with `getTickRate()`, `setTickRate(tickRate)`, `setCurrentDelay(ticks)`
- Transitions check conditions and change states
- EntityState: `INIT` → `ACTIVE_SERVER`/`ACTIVE_CLIENT` → `INACTIVE`

Worker AIs extend `AbstractEntityAIInteract` or similar and implement work-specific logic in state-machine-driven `tick()` calls.

---

## 7. Building System

### 7.1 IBuilding

Path: `com.minecolonies.api.colony.buildings.IBuilding`

Extends `IBuildingContainer`, `IBuildingModuleContainer`, `IRequestResolverProvider`, `IRequester`, `ICommonBuilding`

Every building has:
- A `BuildingEntry` (registry type)
- A position (hut block location)
- A level (0-5, where 0 = undeployed, 5 = max)
- A schematic/blueprint (from Structurize)
- Modules (customizable behavior components)

Key methods:
```java
String getCustomName();
String getBuildingDisplayName();
boolean isBuilt();
boolean isPendingConstruction();
void onUpgradeComplete(Blueprint blueprint, int newLevel);
void onPlacement();
void onDestroyed();
void requestUpgrade(Player player, BlockPos builder);
void requestRemoval(Player player, BlockPos builder);
void requestRepair(BlockPos builder);

// Pickup/delivery
boolean canBeGathered();
boolean createPickupRequest(int pickUpPrio);

// Requests
<R extends IRequestable> IToken<?> createRequest(ICitizenData citizen, R requested, boolean async);
<R extends IRequestable> IToken<?> createRequest(R requested, boolean async);
boolean hasOpenSyncRequest(ICitizenData citizen);
Collection<IRequest<?>> getOpenRequests(int citizenid);
void overruleNextOpenRequestWithStack(ItemStack stack);

// Inventory
Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> getRequiredItemsAndAmount();
ItemStack forceTransferStack(ItemStack stack, Level world);
int buildingRequiresCertainAmountOfItem(ItemStack stack, List<ItemStorage> alreadyKept, boolean inventory);

// Serialization (buffer type differs: NeoFriendlyByteBuf vs FriendlyByteBuf)
void serializeToView(/*RegistryFriendlyByteBuf | FriendlyByteBuf*/ buf, boolean fullSync);
```

### 7.2 BuildingEntry Registry

Path: `com.minecolonies.api.colony.buildings.registry.BuildingEntry`

Builder pattern:
```java
new BuildingEntry.Builder()
    .setBuildingBlock(ModBlocks.blockHutFlorist)
    .setBuildingProducer((colony, pos) -> new BuildingFlorist(colony, pos))
    .setBuildingViewProducer(() -> BuildingFloristView::new)
    .setRegistryName(ResourceLocation.parse("minecolonies:florist"))
    .createBuildingEntry();
```

Each `BuildingEntry` also holds a list of `ModuleProducer` entries that define what modules a building has.

### 7.3 Building Modules

Path: `com.minecolonies.api.colony.buildings.modules.IBuildingModule`

Modules are pluggable behavior components attached to buildings. Common modules include:

| Module | Purpose |
|--------|---------|
| `ISettingsModule` | Per-building settings (e.g., boolean toggles, int sliders) |
| `IAssignsJob` | Links building to a specific job type |
| `ICraftingModule` | Recipe crafting capability |
| `IFarmFieldModule` | Field management for farmers |
| `AbstractAssignedCitizenModule` | Manages assigned workers |
| `IListModule` | Manages a list of something |
| `IPlantationModule` | Plantation field management |

Buildings declare modules via `ModuleProducer` in their `BuildingEntry`:
```java
new BuildingEntry.Builder()
    .addBuildingModuleProducer(new ModuleProducer<>(() -> new SettingsModule(), () -> SettingsModuleView::new))
    ...
```

### 7.4 AbstractBuilding (Implementation)

Path: `com.minecolonies.core.colony.buildings.AbstractBuilding`

Key internal state:
- `modules: List<IBuildingModule>` and `modulesMap: Int2ObjectOpenHashMap<IBuildingModule>`
- `rsDataStoreToken` — request system data
- `requester: IRequester` (BuildingBasedRequester)
- `isBuilt: boolean`
- `customName: String`
- `guardBuildingNear: boolean`
- `pickUpDay: int`
- `prestige: int`

---

## 8. Request System

Paths under `com.minecolonies.api.colony.requestsystem`

The Request System is MineColonies' internal logistics/transport system. Citizens create requests for items they need, and resolvers fulfill them.

### 8.1 IRequestManager

Path: `com.minecolonies.api.colony.requestsystem.manager.IRequestManager`

```java
IToken<?> createRequest(IRequester requester, IRequestable object);
void assignRequest(IToken<?> token);
IToken<?> createAndAssignRequest(IRequester requester, IRequestable object);
IToken<?> reassignRequest(IToken<?> token, Collection<IToken<?>> resolverBlackList);
IRequest<?> getRequestForToken(IToken<?> token);
void updateRequestState(IToken<?> token, RequestState state);
void overruleRequest(IToken<?> token, @Nullable ItemStack stack);
void onProviderAddedToColony(IRequestResolverProvider provider);
void onRequesterRemovedFromColony(IRequester requester);
IPlayerRequestResolver getPlayerResolver();
IRetryingRequestResolver getRetryingRequestResolver();
```

### 8.2 IRequest<R extends IRequestable>

Path: `com.minecolonies.api.colony.requestsystem.request.IRequest`

```java
IToken<?> getId();
TypeToken<? extends R> getType();
RequestState getState();
void setState(IRequestManager manager, RequestState state);
IRequester getRequester();
R getRequest();
R getResult();
IToken<?> getParent();
void addChild(IToken<?> child);
```

Request states: the full lifecycle is managed by the `IRequestManager`, with transitions through `COMPLETED`, `CANCELLED`, etc.

### 8.3 Requestables

Path: `com.minecolonies.api.colony.requestsystem.requestable`

Common requestable types:
- `Stack` — item stack request (match damage/NBT, min count, building-resolvable)
- `IDeliverable` — interface for deliverable requests
- `INonExhaustiveDeliverable` — deliverable that can be partially fulfilled
- Delivery-related: `Delivery`, `Pickup`, `AbstractDeliverymanRequestable`

A `Stack` requestable:
```java
new Stack(ItemStack stack)
// matches by damage, NBT, count, and minCount
// can be resolved by building stock
```

### 8.4 Resolvers

- `IPlayerRequestResolver` — player fulfills via inventory
- `IRetryingRequestResolver` — periodically retries fulfillment
- `IRequestResolverProvider` — buildings act as providers (their inventory resolves requests)
- `IRequestResolver` — generic resolver interface

### 8.5 Crafting / Recipe System

Path: `com.minecolonies.api.crafting`

- `IRecipeManager` — global recipe registry (per-world)
  - `getRecipes()` → `ImmutableMap<IToken<?>, IRecipeStorage>`
  - `addRecipe(IRecipeStorage)` → `IToken<?>`
  - `checkOrAddRecipe(IRecipeStorage)` → `IToken<?>`
  - `getRecipe(IToken<?>)` → `IRecipeStorage`
- `IRecipeStorage` — recipe data
  - `getInput()` → `List<ItemStorage>`
  - `getPrimaryOutput()` → `ItemStack`
  - `getGridSize()` → `int` (4 for 2x2, 9 for 3x3)
  - `getIntermediate()` → `Block` (required workbench/furnace)
  - `canFullFillRecipe(int qty, Map<ItemStorage, Integer> existingReq, IItemHandler... inventories)` → `boolean`

---

## 9. Event System

### 9.1 Event Bus (Both Versions)

Path: `com.minecolonies.api.eventbus`

```java
<T extends IModEvent> void subscribe(Class<T> eventType, EventHandler<T> handler);
void post(IModEvent event);
```

All events implement `IModEvent` (has `UUID getEventId()`).

Events in `com.minecolonies.api.eventbus.events`:

**Colony events** (`com.minecolonies.api.eventbus.events.colony`):
- Colony building events (in `colony/buildings/`)
- Citizen events (in `colony/citizens/`)
- Permission events (in `colony/permissions/`):
  - `PlayerEnteringModEvent`
  - `PlayerLeavingModEvent`

Usage:
```java
IMinecoloniesAPI.getInstance().getEventBus()
    .subscribe(PlayerEnteringModEvent.class, event -> {
        // handle player entering colony
    });
```

### 9.2 Forge Eventhooks (Forge Only)

Path: `com.minecolonies.core.colony.eventhooks`

The Forge version has an additional event hook package separate from the EventBus:

- `BuildingUpgradedEvent`
- `BuildingBuiltEvent`
- `BuildingRepairedEvent`
- `BuildingDeconstructedEvent`
- `CitizenGrownUpEvent`

These extend `AbstractBuildingEvent` or `AbstractEvent` and are fired on the Forge event bus (`MinecraftForge.EVENT_BUS`). NeoForge does not have this package (its building events route through the internal EventBus instead).

---

## 10. Research System

Path: `com.minecolonies.api.research`

### 10.1 IResearchManager

```java
ILocalResearchTree getResearchTree();       // per-colony progress
IResearchEffectManager getResearchEffects(); // active effects
ResourceLocation getResearchEffectIdFrom(Block block);
void checkAutoStartResearch();
```

### 10.2 Global Research

`IGlobalResearchTree` — accessed via `IMinecoloniesAPI.getInstance().getGlobalResearchTree()`

### 10.3 Research Effects & Requirements

Registries:
- `ModResearchEffects.ResearchEffectEntry`
- `ModResearchRequirements.ResearchRequirementEntry`

Effects modify colony/citizen behavior (e.g., `SHIELD_USAGE`, increased block breaking, etc.).

---

## 11. Quest System

Path: `com.minecolonies.api.quests`

Key interfaces:
- `IQuestManager` — manages quests for a colony
- `IQuestTemplate` — quest definition
- `IQuestInstance` — active quest instance
- `IQuestObjectiveTemplate` — objective within a quest
- `IQuestRewardTemplate` — reward definition
- `IQuestTriggerTemplate` — trigger for starting quests
- `IQuestGiver` — entity that can give quests (citizens)
- `IQuestParticipant` — entity participating in quests

Registries (via `IMinecoloniesAPI`):
- `getQuestRewardRegistry()`
- `getQuestObjectiveRegistry()`
- `getQuestTriggerRegistry()`
- `getQuestDialogueAnswerRegistry()`

---

## 12. Interaction System (Chat/Notifications)

Path: `com.minecolonies.api.colony.interactionhandling`

Citizens can show interaction responses (request-based messages, status notifications). 

- `ICitizenInteractionResponseHandler` — per-citizen interaction handling
- Registry: `getInteractionResponseHandlerRegistry()`
- `ChatPriority` — priority levels for queuing messages
- `RequestBasedInteraction` — interactions triggered by request states (e.g., "I need wood!")

---

## 13. Permission System

Path: `com.minecolonies.api.colony.permissions`

### 13.1 Action Enum

Bit-flag based permissions. Key actions (30 total):
```
ACCESS_HUTS, PLACE_HUTS, BREAK_HUTS, EDIT_PERMISSIONS,
MANAGE_HUTS, RECEIVE_MESSAGES, PLACE_BLOCKS, BREAK_BLOCKS,
ATTACK_CITIZEN, ATTACK_ENTITY, TELEPORT_TO_COLONY,
EXPLODE, RALLY_GUARDS, HURT_CITIZEN, HURT_VISITOR,
MAP_BORDER, MAP_DEATHS, ACCESS_TOGGLEABLES, ...
```

### 13.2 IPermissions

- `Rank` enum (OWNER, OFFICER, etc.)
- `ColonyPlayer` — player with rank info
- Check via `getPermissions().hasPermission(player, Action.PLACE_BLOCKS)`

---

## 14. Network / Data Sync

- Colony data syncs via buffer packets (NeoForge: `RegistryFriendlyByteBuf`, Forge: `FriendlyByteBuf`)
- `IColonyPackageManager` handles packaging
- `IColonyView` receives updates: `handleColonyViewMessage()`, `handlePermissionsViewMessage()`, etc.
- Building views sync via `serializeToView(Neo: RegistryFriendlyByteBuf / Forge: FriendlyByteBuf, boolean fullSync)`

Network packet classes (examples, package structure differs between versions):
- `ColonyViewMessage`, `PermissionsMessage`
- `ColonyViewCitizensMessage`, `ColonyViewWorkOrderMessage`
- `BuildingViewMessage`
- `OpenChestMessage`, `TransferItemsMessage`
- `TryResearchMessage`, `AssignFieldMessage`

**Buffer types** differ between versions:
- **NeoForge**: `RegistryFriendlyByteBuf` (supports codec-based object serialization)
- **Forge**: `FriendlyByteBuf` (simpler, no codec registry)
- Forge also has `com.minecolonies.api.network.IMessage` interface for packets; NeoForge uses the standard payload handler pattern.

---

## 15. Common Integration Patterns

### 15.1 Getting the API

```java
IMinecoloniesAPI api = IMinecoloniesAPI.getInstance();
IColonyManager colonyManager = api.getColonyManager();
```

### 15.2 Getting a Colony from a World Position

```java
IColony colony = IColonyManager.getInstance().getIColony(world, blockPos);
// Server-side: returns Colony
// Client-side: returns ColonyView
```

### 15.3 Getting a Citizen Entity's Data

```java
// From entity
AbstractEntityCitizen citizen = (AbstractEntityCitizen) entity;
ICitizenData data = citizen.getCitizenData();
// or from view:
ICitizenDataView dataView = citizen.getCitizenDataView();
```

### 15.4 Iterating Citizens in a Colony

```java
// Server side only
colony.getCitizenManager().getCitizens().values()
    .forEach(citizenData -> { ... });
```

### 15.5 Checking if a Player Has Permission

```java
colony.getPermissions().hasPermission(player, Action.PLACE_BLOCKS);
```

### 15.6 Creating a Building-Level Request

```java
// Create a request for 64 dirt blocks
IToken<?> token = building.createRequest(
    citizenData,
    new Stack(new ItemStack(Blocks.DIRT, 64)),
    false // not async
);
```

### 15.7 Triggering a Raid

```java
colony.getRaiderManager().raiderEvent(
    new RaidSettings()
        .setPath(ResourceLocation.parse("minecolonies:raid/amazon"))
        .setNumberOfRaiders(20)
        .setRaidLevel(5)
);
```

### 15.8 Subscribing to Colony Events

```java
IMinecoloniesAPI.getInstance().getEventBus()
    .subscribe(PlayerEnteringModEvent.class, event -> {
        // event.getColony(), event.getPlayer()
    });
```

### 15.9 Getting Building Type Info

```java
// Check what type a building is
BuildingEntry entry = building.getBuildingType();
// Get the registry name
ResourceLocation name = entry.getRegistryName();
// Check if building is of a specific type
if (building instanceof BuildingTownHall) { ... }
```

### 15.10 Checking Research Effects

```java
// Check if a research effect is active
IResearchEffectManager effects = colony.getResearchManager().getResearchEffects();
double shieldUsage = effects.getEffect(ResearchConstants.SHIELD_USAGE, double.class);
```

---

## 16. Mixin Integration Points

Key mixin targets in the talking-colonists codebase (9 mixins):

| Target | Purpose |
|--------|---------|
| Citizen/Civilian entity spawn | Hook into entity construction |
| Colony creation/load | Get colony lifecycle events |
| Citizen interaction | Intercept player-citizen right-clicks |
| Chat/sound events | Intercept citizen speech |
| Entity tick | Hook into citizen AI ticks |
| Building events | Building placement/destruction |
| Request system | Item request fulfillment |

When mixin'ing into MineColonies classes, target the `com.minecolonies.core` package (implementation classes), not the API interfaces.

---

## 17. Key Packages Summary

| Package | Contents |
|---------|----------|
| `com.minecolonies.api` | `IMinecoloniesAPI` root interface, proxy |
| `com.minecolonies.api.colony` | `IColony`, `IColonyView`, `ICitizenData`, `ICitizenDataView` |
| `com.minecolonies.api.colony.buildings` | `IBuilding`, registry, modules |
| `com.minecolonies.api.colony.jobs` | `IJob`, registry, `JobEntry` |
| `com.minecolonies.api.colony.managers.interfaces` | `IRaiderManager`, `ICitizenManager`, etc. |
| `com.minecolonies.api.colony.permissions` | `IPermissions`, `Action`, `Rank` |
| `com.minecolonies.api.colony.requestsystem` | Full request/resolver system |
| `com.minecolonies.api.colony.workorders` | Build/upgrade/repair orders |
| `com.minecolonies.api.crafting` | `IRecipeManager`, `IRecipeStorage` |
| `com.minecolonies.api.entity.citizen` | `AbstractEntityCitizen`, `Skill`, `VisibleCitizenStatus` |
| `com.minecolonies.api.entity.ai` | AI state machine interfaces |
| `com.minecolonies.api.entity.pathfinding` | Custom pathfinding |
| `com.minecolonies.api.research` | Research tree/effects |
| `com.minecolonies.api.quests` | Quest system |
| `com.minecolonies.api.eventbus` | Internal event bus |
| `com.minecolonies.core` | `MineColonies` main mod class |
| `com.minecolonies.core.colony` | `Colony` implementation |
| `com.minecolonies.core.colony.buildings` | `AbstractBuilding` + worker building impls |
| `com.minecolonies.core.colony.jobs` | Job implementations |
| `com.minecolonies.core.entity.ai` | Worker AI implementations |
| `com.minecolonies.core.network` | Network messages |
| `com.minecolonies.core.blocks` | Hut block implementations |

---

## 19. Mod Dependencies

| Dependency | Version |
|-----------|---------|
| Structurize (LDTTeam) | Required (blueprints/schematics) |
| Simple Voice Chat | Optional (for voice features) |
| YACL | Optional (config GUI) |
| BlockUI | Embedded (UI framework) |
| **NeoForge version**: NeoForge >=21.1.x, Minecraft 1.21.1 |
| **Forge version**: Forge >=40.x, Minecraft 1.20.1 |

---

## 20. Version Differences: NeoForge vs Forge

The following table summarizes all known API and implementation differences between the two active MineColonies branches.

### 20.1 Networking

| Aspect | NeoForge (1.21.1) | Forge (1.20.1) |
|--------|--------------------|----------------|
| Buffer type | `RegistryFriendlyByteBuf` | `FriendlyByteBuf` |
| Packet base | Payload handler pattern (via `RegisterPayloadHandlersEvent`) | `IMessage` interface (`com.minecolonies.api.network.IMessage`) |
| Helper | none | `com.minecolonies.api.network.PacketUtils` |

### 20.2 Registry Types

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| Registry return type | `net.minecraft.core.Registry<T>` | `net.minecraftforge.registries.IForgeRegistry<T>` |
| Event for registry creation | `NewRegistryEvent` | `NewRegistryEvent` (same name, different package) |

### 20.3 NBT Serialization

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| `IColony.write()` | `write(CompoundTag, HolderLookup.Provider)` | `write(CompoundTag)` |
| `IColony.read()` | `read(CompoundTag, HolderLookup.Provider)` | `read(CompoundTag)` |
| HolderLookup.Provider | Required for codec-based serialization | Not used |

### 20.4 Config System

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| Config class | `Configurations<ClientConfiguration, ServerConfiguration, CommonConfiguration>` | Single `Configuration` class |
| Import | `com.ldtteam.common.config.Configurations` | `.minecolonies.api.configuration.Configuration` |

### 20.5 Event/Tick Classes

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| Server tick | `ServerTickEvent.Pre` (NeoForge event) | `TickEvent.ServerTickEvent` (Forge event) |
| Client tick | `ClientTickEvent.Pre` | `TickEvent.ClientTickEvent` |
| World tick | `LevelTickEvent.Pre` | `TickEvent.LevelTickEvent` |
| Mod bus | `neoforged.bus.api.IEventBus` | `net.minecraftforge.eventbus.api.IEventBus` |
| Forge bus | `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| FML container | `FMLModContainer` | `FMLJavaModLoadingContext` |

### 20.6 Colony Flag/Banner

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| `IColony.getColonyFlag()` | Returns `BannerPatternLayers` | Returns `ListTag` |
| `IColony.setColonyFlag()` | Accepts `BannerPatternLayers` | Accepts `ListTag` |

### 20.7 Capabilities vs Data Components

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| Chunk persistence | Claim system (`com.minecolonies.api.colony.claim`) | Capabilities: `CLOSE_COLONY_CAP`, `CHUNK_STORAGE_UPDATE_CAP` |
| Item data | Data Components (`com.minecolonies.api.items.component`): `ColonyId`, `BuildingId`, `HutBlockData`, `RallyData`, `PatrolTarget`, `WarehouseSnapshot`, `PermissionMode`, `Timestamp`, `AdventureData`, `Desc` | Not available (no data component system) |
| `IColony.writeToItemStack()` | Has default method using `ColonyId` data component | Does not exist |
| `IBuilding.writeToItemStack()` | Has default method using `BuildingId` data component | Does not exist |
| Item capability registration | `RegisterCapabilitiesEvent` | `RegisterCapabilitiesEvent` (Forge, different package) |
| IItemHandler import | `net.neoforged.neoforge.items.IItemHandler` | `net.minecraftforge.items.IItemHandler` |

### 20.8 Event Hooks (Forge Only)

`com.minecolonies.core.colony.eventhooks` exists only in Forge:

- `BuildingBuiltEvent`, `BuildingUpgradedEvent`, `BuildingRepairedEvent`, `BuildingDeconstructedEvent`
- `CitizenGrownUpEvent`
- Extend `AbstractBuildingEvent` / `AbstractEvent`
- Fired on `MinecraftForge.EVENT_BUS`

NeoForge routes building events through `IMinecoloniesAPI.getEventBus()` instead.

### 20.9 Enchants (Forge Only)

`com.minecolonies.core.enchants.RaiderDamageEnchant` exists only in Forge. NeoForge does not have this package.

### 20.10 Loot (Forge Only)

`com.minecolonies.core.loot.SupplyLoot` exists only in Forge. NeoForge handles loot differently.

### 20.11 Research Costs (Forge Only)

`com.minecolonies.api.research.costs` package exists only in Forge:
- `IResearchCost` interface
- Implementations: `SimpleItemCost`, `ListItemCost`, `TagItemCost`
- Accessed via `IMinecoloniesAPI.getResearchCostRegistry()`

NeoForge integrates research costs differently within the research effect/requirement system.

### 20.12 Server/World Types

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| `IColonyManager.createColony()` | `ServerLevel` parameter | `Level` parameter |
| `IColonyManager.deleteColonyByWorld()` | `ServerLevel` parameter | `Level` parameter |
| `IColonyManager.handleColonyViewMessage()` | No `Level` parameter | Extra `Level world` parameter |
| `IColonyManager.setCapLoaded()` | Not present | Has this method |
| Claim management (`addClaimData`, `getClaimData`, `addNewChunk`, `addColonyDirect`) | Present in `IColonyManager` | Not present |

### 20.13 Building Sort

`IBuilding.sort()` in NeoForge takes `(HolderLookup.Provider, CombinedItemHandler)`, in Forge takes `(CombinedItemHandler)` only.

### 20.14 Main Mod Class

| Aspect | NeoForge | Forge |
|--------|----------|-------|
| `MineColonies.java` size | ~507 lines | ~306 lines |
| Registration style | Individual deferred register per subsystem | More centralized registrations |
| Debug messages | Has `DebugEnableMessage`, `DebugEnablePathfindingMessage`, `DebugOutputMessage`, `QueryCitizenAIHistoryMessage` | Does not have these |

### 20.15 Forge-only Package Summary

| Package | Purpose |
|---------|---------|
| `com.minecolonies.api.network` | `IMessage` + `PacketUtils` for Forge networking |
| `com.minecolonies.api.research.costs` | Research cost types (`SimpleItemCost`, `ListItemCost`, `TagItemCost`) |
| `com.minecolonies.core.colony.eventhooks` | Building/citizen event hooks on Forge event bus |
| `com.minecolonies.core.enchants` | `RaiderDamageEnchant` |
| `com.minecolonies.core.loot` | `SupplyLoot` |
| `com.minecolonies.core.event/capabilityproviders` | Capability provider implementations |
| `com.minecolonies.core/network/messages/splitting` | Large message splitting logic |

### 20.16 NeoForge-only Package Summary

| Package | Purpose |
|---------|---------|
| `com.minecolonies.api.colony.claim` | `ChunkClaimData`, `IChunkClaimData` — claim-based chunk persistence |
| `com.minecolonies.api.colony.savedata` | `ServerColonySaveData` — data-driven save system |
| `com.minecolonies.api.items.component` | Data components (`ColonyId`, `BuildingId`, `HutBlockData`, `RallyData`, `PatrolTarget`, `WarehouseSnapshot`, `PermissionMode`, `Timestamp`, `AdventureData`, `Desc`) |
| `com.minecolonies.core.datalistener.util` | Data listener utilities |

### 20.17 Cross-Version Compatibility Tips

When writing code that targets **both** versions via the Stonecutter conditional compilation system (`/*? if neoforge {*/` / `/*? if forge {*/`):

1. **Buffer types**: Use `/*? if neoforge {*/ RegistryFriendlyByteBuf /*?} else {*/ FriendlyByteBuf /*?}*/`
2. **NBT serialization**: Conditionally include/exclude `HolderLookup.Provider` parameter
3. **Registry access**: Use `/*? if neoforge {*/ Registry<BuildingEntry> /*?} else {*/ IForgeRegistry<BuildingEntry> /*?}*/`
4. **Config**: Guard `Configurations` vs `Configuration` with platform checks
5. **ServerLevel vs Level**: Use `Level` for cross-version, or conditionally cast
6. **Events**: Use NeoForge events on 1.21.1, Forge events on 1.20.1
7. **Data Components**: Guard NeoForge-only component usage (`ColonyId`, `BuildingId`)
8. **Banner**: Guard `BannerPatternLayers` vs `ListTag`
9. **Forge-only features** (eventhooks, research costs, enchants, loot): Guard with `/*? if forge {*/`

---

## 21. Citizen Daily Lifecycle

1. **Wake up** (morning): Citizens exit beds, `onWakeUp()` called on job and building
2. **Eat**: Check food inventory, eat if hungry (saturation affects work speed)
3. **Work**: AI state machine drives job-specific tasks (crafting, mining, farming, etc.)
4. **Dump inventory**: When `actionsDone` reaches threshold, dump items to building chest
5. **Pickup requests**: Deliveryman brings requested items
6. **Leisure**: If `getLeisureTime() > 0`, citizen may idle
7. **Sleep** (night): Citizens return to bed
8. **Happiness processing**: `processDailyHappiness()` runs daily
9. **Disease check**: Random disease chance based on `getDiseaseModifier()`
10. **Reproduction**: Married citizens may have children (handled by `IReproductionManager`)
