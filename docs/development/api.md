# API Reference

For developers integrating with or extending the mod.

## PromptProvider SPI

The `me.sshcrack.mc_talking.api.prompt` package defines a service provider interface for custom prompt generation.

| Interface/Class | Purpose |
|-----------------|---------|
| `CitizenPromptProvider` | Implement to customize prompt generation. Receives a `CitizenPromptView` with full context. |
| `CitizenPromptService` | Registry where custom providers are registered. |
| `CitizenPromptView` | Data object with citizen info, colony stats, memories, events, rumors, broadcasts, personality, etc. |
| View sub-package | Component view objects for each prompt section. |

### Custom Prompt Provider Example

```java
public class MyCustomProvider implements CitizenPromptProvider {
 @Override
 public String generatePrompt(CitizenPromptView view) {
 // Build custom prompt from view data
 return "Custom prompt with " + view.getCitizenName();
 }
}
```

## Tool Registration

AI function-calling tools are registered in `AITools.register()`. Each tool extends `FunctionAction` (abstract base) which has two categories:

- `GeneralFunctionAction` - Available in all conversation contexts.
- `PlayerFunctionAction` - Only available during player conversations.

### Adding a Custom Tool

1. Create a class extending `GeneralFunctionAction` or `PlayerFunctionAction`.
2. Implement the `execute(AbstractEntityCitizen, IColony, JsonObject)` method.
3. Register in `AITools.register()` by adding to the appropriate map.

## MineColonies Integration

The `me.sshcrack.mc_talking.duck` package contains duck-type interfaces for MineColonies extension points. Key interfaces:

- `CitizenDataPersonalityExtended` - Added via mixin for personality storage.

## Events

| Event Handler | Purpose |
|---------------|---------|
| `ColonyEventSubscriber` | Listens for MineColonies colony lifecycle events (births, deaths, building changes) |

## Key Source Packages

| Package | Purpose |
|---------|---------|
| `me.sshcrack.mc_talking.api.prompt` | PromptProvider SPI |
| `me.sshcrack.mc_talking.manager.tools` | AI tool registration |
| `me.sshcrack.mc_talking.duck` | MineColonies duck-type interfaces |
| `me.sshcrack.mc_talking.listener` | Event subscribers |
