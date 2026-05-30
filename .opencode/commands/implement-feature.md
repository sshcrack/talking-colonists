---
description: Spin up parallel agents to implement chosen features from the explore-features pipeline
agent: build
---

# Feature Implementation — talking-colonists

The user wants to implement the following features in parallel. The list is: $ARGUMENTS

For EACH feature in that list, spawn a separate @general subagent with this exact task:

---
You are implementing a feature for a Minecolonies Minecraft mod that adds Gemini AI voice chat to MineColonies citizens.

Feature: <feature name and description>

## Before you start — read these reference files to understand patterns

Read ALL of these to match the codebase conventions:

### Patterns to follow

1. **Mixin for MineColonies hook** → read `src/main/java/me/sshcrack/mc_talking/mixin/RaidManagerMixin.java`
   - Uses `@Mixin(value = RaidManager.class, remap = false)` and `@Shadow` + `@Final` for private fields
   - Note how it accesses `colony` via a shadowed private field and calls methods on `(RaidManager)(Object)this`
2. **New config option** → read `src/main/java/me/sshcrack/mc_talking/config/McTalkingConfig.java`
   - YACL annotations: `@TickBox`, `@IntSlider`, `@FloatSlider`, `@TextField`, `@Category`
   - Nested category classes for grouping
3. **Duck interface + mixin for extending citizen data** → read
   - `src/main/java/me/sshcrack/mc_talking/duck/CitizenDataMemoryExtended.java`
   - `src/main/java/me/sshcrack/mc_talking/mixin/CitizenDataMixin.java`
4. **Adding prompt data to citizen view** → read
   - `src/main/java/me/sshcrack/mc_talking/api/prompt/view/CitizenPromptView.java` (add fields here)
   - `src/main/java/me/sshcrack/mc_talking/manager/CitizenPromptViewFactory.java` (populate fields here)
   - `src/main/java/me/sshcrack/mc_talking/manager/DefaultCitizenPromptProvider.java` (use fields in prompt text)
5. **New AI function-calling tool** → read `src/main/java/me/sshcrack/mc_talking/manager/tools/specific/GetCitizenInfoAction.java` or `DescribeSurroundingsAction.java`
   - Register in `AITools.java`
6. **Gemini prompt integration pattern** → read `DefaultCitizenPromptProvider.java`'s `generateCitizenRoleplayPrompt()` method
   - System instructions are concatenated as one long string; no templates
   - Emotional/happiness profiling is embedded inline
7. **Listener on MineColonies EventBus** → no existing example, so use `IMinecoloniesAPI.getInstance().getEventBus().subscribe()` pattern
   - Register listener in `McTalking.java` constructor or `ServerEventHandler.onServerStart()`
   - Handle event types like `CitizenDiedModEvent`, `BuildingAddedModEvent`, etc.
   - Inspect those event classes from the source jar for their API

### Stonecutter multi-loader constraints

- Conditional compilation with `/*? if neoforge {*/` / `/*? if forge {*/`
- When both loaders need the same logic but different imports, use the stonecutter blocks around imports
- For platform-specific things (registries, events), check what the existing code does
- **Entity.level() can return null in Forge 1.20.1** — null-check before using it
- Access transformers for MineColonies classes: `src/main/resources/aw/<version>.cfg` (Forge) or `src/main/resources/aw/<version>.accesswidener` (NeoForge)

### Code conventions

- Keep files under 400 lines if possible; split into multiple classes if needed
- 4-space indent for Java
- Single class imports only (import-on-demand threshold = 999)
- Private constructor for utility classes
- Follow naming: camelCase methods, PascalCase classes, UPPER_SNAKE constants
- Use the same logging pattern: `McTalking.LOGGER.info/warn/error` (not System.out or log4j directly)

### MineColonies API access patterns

- Access colony: `IColonyManager.getInstance().getColonyByPos(...)` or via citizen's `getCitizenColonyHandler().getColony()`
- Access citizen data from entity: `((AbstractEntityCitizen) entity).getCitizenData()`
- Access MineColonies EventBus: `IMinecoloniesAPI.getInstance().getEventBus().subscribe(EventClass.class, handler)`
- Access colony managers: `colony.getRaiderManager()`, `colony.getEventManager()`, `colony.getStatisticsManager()`, `colony.getEventDescriptionManager()`, `colony.getQuestManager()`, `colony.getVisitorManager()`, `colony.getWorkManager()`
- Access citizen handlers: `citizenData.getCitizenHappinessHandler()`, `.getCitizenJobHandler()`, `.getCitizenDiseaseHandler()`, `.getCitizenSleepHandler()`, `.getCitizenMournHandler()`

## Implementation steps

Follow these in order:

1. **Run**: `wt create <branch-slug>` — worktree for isolated development
   - Use the printed worktree path for ALL subsequent operations

2. **Create a GitHub issue**:
   ```
   gh issue create --title "<Feature title>" --body "<2-3 sentence description of what this adds and why>"
   ```
   Save the issue number from the output.

3. **Implement the feature**:
   - Before writing code, search for similar existing implementations in the codebase (grep for the API class you need)
   - Write clean Java matching the conventions above
   - Respect Stonecutter conditional compilation — test-logic that differs between Forge and NeoForge must use `/*? if neoforge {*/` / `/*? if forge {*/`
   - Make focused commits as you go (at minimum: one commit per logical change)
   - If you add new config options, they go in `McTalkingConfig.java`
   - If you add new prompt data, modify `CitizenPromptView` record, `CitizenPromptViewFactory`, and `DefaultCitizenPromptProvider`

4. **Verify**:
   - Check that the mod compiles for the active Stonecutter version: `./gradlew buildAndCollect`
   - If there are compile errors, check if they're version-specific (1.20.1 vs 1.21.1) and add conditional compilation
   - Verify no new imports were added that are forge-specific without conditional blocks

5. **When done, stage and commit**:
   ```
   git add -A && git commit -m "feat: <short description>"
   git push -u origin <branch>
   ```

6. **Open a draft PR**:
   ```
   gh pr create --draft \
   --title "feat: <Feature title>" \
   --body "## Summary\n<What was done and why>\n\nCloses #<issue number>"
   ```

7. Report back: the issue URL, PR URL, and branch name.

---

Spawn all agents in parallel. When all are done, summarize: list each feature with its issue link and draft PR link.
