# Plan: `feat/animal-and-livestock-awareness`

## Context

The mod has per-job idle thoughts in `MumblingTopicHelper` for herder-type jobs (shepherd, cowboy, swineHerder, chickenHerder, rabbitHerder), but these are **generic** — they read like "You're thinking about your flock" without referencing any actual colony data. The `IAnimalManager` and `IAnimalData` APIs exist on `IColony` but are completely unused across the entire codebase.

The `feat/self-aware-events` PR added `DescribeBuildingAction` enrichment for some building types but did not touch animals. No prompt system currently surfaces livestock counts, health, or notable events.

## Objective

Make citizens aware of the colony's registered managed animals (cavalry horses). Surface animal count and status in the prompt system. Expand `DescribeBuildingAction` with a `ranch`/`stable` query mode for animal-related buildings. Add a config toggle.

**API limitation**: `IAnimalManager` tracks only managed animals (cavalry mounts for guards), not farm livestock (sheep, cows, chickens, pigs, rabbits) which herders work with. Herder-specific idle thoughts therefore remain generic but gain colony-level context when managed animals exist.

## Implementation Steps

### 1. Read `IManagedAnimal` and `IAnimalData` from the MineColonies sources jar

Extract these interfaces first to understand what data is available at runtime. Key known accessors:
- `IAnimalManager.getAnimals()` → `List<IAnimalData>`
- `IAnimalManager.getCurrentAnimalCount()` → `int`
- `IAnimalData.getUUID()`, `getHomeBuilding()`, `getLastPosition()`

**Important**: `IAnimalData` represents managed animals (cavalry horses for guards), not farm livestock. Herders in MineColonies work with regular Minecraft entities that are NOT tracked via `IAnimalManager`. For most colonies, `getCurrentAnimalCount()` will be 0. If the count is 0, skip the entire section silently.

Key implication for herder thoughts (step 4): concrete animal counts are only available for cavalry horses. Herders' actual livestock (sheep, cows, etc.) cannot be counted through this API. Keep herder thoughts generic but optionally reference the managed animal count when it exists.

### 2. Add animal data to `CitizenPromptView`

In `manager/CitizenPromptViewFactory.java`:
- Add `extractAnimalInfo(ICitizenData data)` helper method:
  ```java
  private static @Nullable String extractAnimalInfo(ICitizenData data) {
      if (!McTalkingConfig.INSTANCE.instance().enableAnimalAwareness) return null;
      IColony colony = data.getColony();
      if (colony == null) return null;
      IAnimalManager mgr = colony.getAnimalManager();
      int count = mgr.getCurrentAnimalCount();
      if (count == 0) return null;
      List<IAnimalData> animals = mgr.getAnimals();
      // Build a summary: e.g. "3 animals (2 horses, 1 unassigned)"
      return count + " managed animal(s)";
  }
  ```
- Pass result to the `CitizenPromptView` constructor

In `api/prompt/view/CitizenPromptView.java`:
- Add `@Nullable String animalSummary` component (append at end, before closing paren)
- Add Javadoc

### 3. Inject animal context into `DefaultCitizenPromptProvider`

In `manager/DefaultCitizenPromptProvider.java`:
- In `addObservations()` method, add a block when `view.animalSummary()` is non-null:
  ```
  - Colony animals: {{animalSummary}}
  ```
- For herder-type citizens (detected by job title containing "shepherd", "cowboy", "swine herder", "chicken herder", "rabbit herder"), prepend an extra line:
  ```
  - You work with animals. The colony has {{animalSummary}}.
  ```

**Design note**: Reuse the existing pattern — guard sections behind the config toggle, build in `addObservations()`, keep the prompt readable.

### 4. Update `MumblingTopicHelper` for animal-aware idle thoughts

In `util/MumblingTopicHelper.java`:
- The `buildJobThought()` method already handles herder jobs with generic text
- Since `IAnimalManager` only tracks cavalry mounts (not farm livestock), the approach is tiered:
  - **Tier 1** (colony has managed animals > 0): Prepend a single sentence to the existing generic herder thought: "There are {{count}} horses in the colony's stables — {{detail}}." The detail could reference readiness (`getCombatCooldown()`) or location.
  - **Tier 2** (no managed animals): Leave the existing generic herder thoughts unchanged — they remain text-only ("You're thinking about your flock") without data.
- Add a new method `buildAnimalContext(AbstractEntityCitizen citizen)` that returns a `@Nullable String` with the managed-animal context line, or `null` if the toggle is off or count is 0
- In `buildJobTopic()`, for herder jobs, call `buildAnimalContext()` and prepend its result to the job thought if non-null

### 5. Extend `DescribeBuildingAction` with stable/ranch query

The `feat/self-aware-events` PR rewrote `DescribeBuildingAction` with per-building-type context. This step adds a `stable` query type:
- In `manager/tools/DescribeBuildingAction.java` (create if not merged yet; otherwise modify):
  - Add `"stable"` to the enum of building types the tool accepts
  - When queried with `type: "stable"`:
    - Query `IColony.getAnimalManager().getAnimals()`
    - List each animal: ID, UUID, position, home building name (if any)
    - Return total count and individual entries
  - Handle the edge case where the colony has no `IAnimalManager` or 0 animals

### 6. Add config toggle

In `config/McTalkingConfig.java`:
- Add a new group under the `citizens` category: `group = "animal_awareness"` (you may need to register the group with an `@AutoGen` annotation — check how existing groups work in the config; if `ListGroup` isn't the right container, check how colony_diplomacy group is declared)
- `@TickBox @SerialEntry public boolean enableAnimalAwareness = true;`

In `src/main/resources/assets/mc_talking/lang/en_us.json`:
- Add translations:
  ```json
  "yacl3.config.mc_talking:config.category.citizens.group.animal_awareness": "Animal Awareness",
  "yacl3.config.mc_talking:config.enableAnimalAwareness": "Enable Animal Awareness",
  "yacl3.config.mc_talking:config.enableAnimalAwareness.desc": "If enabled, citizens reference the colony's animals in idle thoughts and conversations. Herders get data-driven content about actual livestock instead of generic job thoughts."
  ```

## Files to Touch

| File | Change |
|------|--------|
| `api/prompt/view/CitizenPromptView.java` | Add `animalSummary` field |
| `manager/CitizenPromptViewFactory.java` | Add `extractAnimalInfo()`, pass to view |
| `manager/DefaultCitizenPromptProvider.java` | Add animal section in `addObservations()` |
| `util/MumblingTopicHelper.java` | Add `buildAnimalAwareJobThought()`, wire into `buildJobThought()` for herder jobs |
| `manager/tools/DescribeBuildingAction.java` | Add `ranch` query type |
| `config/McTalkingConfig.java` | Add `enableAnimalAwareness` toggle + group |
| `lang/en_us.json` | Translations |

## MineColonies API References

- `IColony.getAnimalManager()` → `IAnimalManager` — `com.minecolonies.api.colony.managers.interfaces.IAnimalManager`
  - `getCurrentAnimalCount()` → `int`
  - `getAnimals()` → `List<IAnimalData>`
- `IAnimalData` — `com.minecolonies.api.colony.IAnimalData`
  - `getId()` → `int`
  - `getUUID()` → `UUID`
  - `getHomeBuilding()` → `@Nullable IBuilding`
  - `getLastPosition()` → `BlockPos`
  - `getCombatCooldown()` → `float` (horses)

## Verification

- `./gradlew :1.21.1-neoforge:compileJava` passes without errors
- Colony with 0 managed animals → no animal content in prompts (feature is transparent)
- Colony with cavalry horses → `describe_building(type: "stable")` returns animal list with details
- Herder citizen in colony with horses → mumbling includes `buildAnimalContext()` line prepended to generic job thought
- `enableAnimalAwareness = false` → all new content suppressed, herders fall back to existing generic thoughts
- `/talking_colonists prompt <citizen>` shows colony animal summary in Observations when applicable

## Key Patterns to Follow

- **Null safety**: `IAnimalManager` / `getAnimals()` could return empty; guard with null/empty checks. Return `null` from `extractAnimalInfo` to signal "skip this section"
- **Prompt injection**: See `DefaultCitizenPromptProvider.addObservations()` — conditional blocks gated by config + null checks, appended to a `StringBuilder` which is prepended to `prompt`
- **MumblingTopicHelper pattern**: See the existing per-job `buildJobThought()` — each job returns a `MiscUtil.pick()` string or `null`; the new `buildAnimalContext()` returns a nullable string that `buildJobTopic()` prepends for herder jobs
- **Config group addition**: Check how `"colony_diplomacy"` group is declared in `McTalkingConfig.java` (it uses `@AutoGen(category = "citizens", group = "colony_diplomacy")` with a `@TickBox`) — replicate for `"animal_awareness"`. If YACL complains about an unregistered group, check if groups need prior registration or just the first annotated field creates them
- **Stonecutter compatibility**: Any imports from MineColonies must not be platform-conditional; both forge and neoforge versions share the same API
