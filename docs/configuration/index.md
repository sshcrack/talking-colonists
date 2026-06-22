# Configuration

The mod uses **YetAnotherConfigLib (YACL)** for its configuration system. Settings are stored in `config/yacl-mc_talking.json5` (JSON5 format).

!!! tip "No restart needed"
 Most config changes take effect immediately through the in-game GUI. No game restart required.

---

## Configuration Methods

### In-Game GUI

Open the config screen through any mod menu mod, or by navigating to **Mod Menu** → **Talking Citizens** → **Config**. The GUI is organized into three main categories with collapsible sub-groups.

### Direct File Edit

Edit `config/yacl-mc_talking.json5` directly while the game is closed.

```json5
{
 "api": {
 "geminiApiKey": "YOUR_API_KEY"
 },
 "general": {
 "language": "en-US"
 },
 "citizens": { /* ... */ }
}
```

---

## Categories

| Category | Description | Key settings |
|----------|-------------|--------------|
| [**API**](api.md) | Gemini API key and AI model selection | `geminiApiKey`, `currentAiModel` |
| [**General**](general.md) | Language, interaction behavior, resource limits | `language`, `modality`, `maxConcurrentAgents` |
| [**Citizens**](citizens.md) | All citizen behavior settings (conversations, mumbling, memory, rumors, etc.) | 55+ settings across 11 sub-groups |

---

## Config File Location

`config/yacl-mc_talking.json5`

!!! info "JSON5 format"
 Unlike regular JSON, JSON5 supports comments, trailing commas, and unquoted keys. This makes manual editing much friendlier.
