---
description: Analyze the MineColonies mod integration and suggest grounded features
agent: plan
---

# Feature Exploration — talking-colonists

You are a feature designer for a Minecraft mod that adds Gemini AI voice chat to MineColonies citizens.

## Step 1 — Catalog existing features

Scan `src/main/java/me/sshcrack/mc_talking/` and build a precise inventory of what
already exists. Read at least the key files below so you can distinguish "done"
from "not done". Pay special attention to stubs, TODOs, and commented-out code.

**Must-read files** (read their full contents):
- `ServerEventHandler.java` — every server-side periodic check and event hook
- `McTalkingConfig.java` — every config toggle (indicates planned/partial features)
- `manager/tools/AITools.java` — registered Gemini function-calling tools
- `manager/tools/specific/JobSpecificAction.java` — known stub
- `conversations/memory/CitizenMemoryGenerator.java` — watch for TODO about thread shutdown
- `mixin/EventDescriptionManagerMixin.java` — intentionally empty; what was it for?
- `mixin/RaidManagerMixin.java` — the one existing colony event hook pattern
- `util/RaidTraumaTracker.java` — the one existing colony event tracker
- `util/MumblingTopicHelper.java` — job-aware topic generation, to avoid overlap with job-specific actions
- `pregen/PregenerationTaskService.java` — pregeneration system
- `conversations/CitizenConversation.java` — citizen-to-citizen conversation flow
- `manager/CitizenPromptViewFactory.java` — what citizen data is already extracted

## Step 2 — Read the MineColonies source jar

You can find the sources jar path by running `scripts/find-minecolonies-sources.sh`.

Extract and read these key API files to discover integration points:

### Event Bus (live colony event hooks)
```
com/minecolonies/api/eventbus/EventBus.java
com/minecolonies/api/IMinecoloniesAPI.java
```
Available events the mod could listen to:
- `CitizenDiedModEvent` — citizen dies (cause of death available)
- `CitizenAddedModEvent` — new citizen born/joined
- `CitizenRemovedModEvent` — citizen exiled or lost
- `CitizenJobChangedModEvent` — citizen changes profession
- `BuildingAddedModEvent` / `BuildingRemovedModEvent` — buildings placed/destroyed
- `BuildingConstructionModEvent` — building upgrades
- `ColonyCreatedModEvent` / `ColonyDeletedModEvent`
- `ColonyPlayerRankChangedModEvent` — player promoted/demoted in colony
- `ColonyNameChangedModEvent`
- `PlayerEnteringModEvent` / `PlayerLeavingModEvent` — player enters/leaves colony area

### Colony managers (data to inject into prompts + trigger AI actions)
```
com/minecolonies/api/colony/IColony.java
com/minecolonies/api/colony/managers/interfaces/
```
Key managers:
- `IRaiderManager` — `canHaveRaiderEvents()`, `willRaidTonight()`, `getLostCitizen()`, `getNumberOfRaidEvents()`
- `IEventManager` — active colony events (raids, spawn events)
- `IEventDescriptionManager` — **already mixed in but the hook is empty** — event history log (past raids, building destructions, etc.)
- `IStatisticsManager` — colony lifetime stats (days, items crafted, citizens recruited, etc.)
- `IVisitorManager` — visiting citizens at the tavern
- `ICitizenManager` — citizen population, children, deaths
- `IGraveManager` — buried citizen data
- `IReproductionManager` — citizen children/births
- `ITravellingManager` — travelling merchants/supply camps
- `IQuestManager` — active and completed quests in the colony
- `IColonyConnectionManager` — diplomacy links to other colonies
- `IWorkManager` — active builder work orders (construction progress)

### Citizen data handlers
```
com/minecolonies/api/colony/ICitizenData.java
com/minecolonies/api/entity/citizen/citizenhandlers/
```
- `ICitizenDiseaseHandler` — `isSick()`, disease type
- `ICitizenSleepHandler` — sleep state, bed position
- `ICitizenInventoryHandler` — inventory contents
- `ICitizenHappinessHandler` — happiness with all modifier details
- `ICitizenMournHandler` — `isMourning()`, mourning duration
- `ICitizenSkillHandler` — skill levels
- `ICitizenJobHandler` — current job
- `ICitizenExperienceHandler` — XP toward next level
- `ICitizenColonyHandler` — colony, home building
- `ICitizenFoodHandler` — saturation details
- `VisibleCitizenStatus` — current visible status enum

### Building & Job system
```
com/minecolonies/api/colony/buildings/IBuilding.java
com/minecolonies/api/colony/jobs/IJob.java
```
- `getBuildingDisplayName()`, `getCustomName()`, `getLevel()` — building info
- Building modules: crafting, settings, entity lists, item lists, minimum stock
- Job registry with all ~30+ job types
- `IGuardBuilding` — guard-specific info (patrol routes, equipment)

### Guest/Visitor system
```
com/minecolonies/api/colony/IVisitorData.java
```
- Recruitment cost, sitting position
- Visitor-specific dialogue at tavern

### Quest system
```
com/minecolonies/api/quests/
```
- `IQuestManager` — available, in-progress, completed quests
- `IDialogueObjectiveTemplate` — quest dialogue tree with player answer options
- `IQuestInstance` — quest giver, participants, rewards

### Other APIs
- `IWorkOrder` — builder work orders (construction/upgrade/deconstruction progress)
- `IChunkClaimData` — colony claims, expansion
- `IResearchManager` / `IGlobalResearchTree` — active/completed research
- `IAnimalManager` — livestock owned by the colony
- `ColonyConnection` — inter-colony connections (trade, war, alliance)
- `IGraveData` — buried citizen info (name, death date, items)

## Step 4 — Suggest 5–8 features

For each feature:

1. **Short slug** (e.g. `feat/colony-event-dialogue`)
2. **One-sentence summary**
3. **~3 bullet points** of what the implementation would touch
4. **MineColonies API references** — specifically cite the classes/interfaces that
   would be used (e.g., `IColony.getEventDescriptionManager().getEventDescriptions()`,
   `IMinecoloniesAPI.getInstance().getEventBus().subscribe()`)
5. **Grounding check** — "This makes sense because [reason]" or "This conflicts with [existing feature] — skip"

## Rules

- Do NOT suggest features that already exist (check carefully)
- Do NOT suggest features that conflict with existing implementation patterns
- Ground every feature in actual MineColonies API classes you found in the source jar
- Output ONLY the feature list — no preamble, no summary
