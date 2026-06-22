# Audio Pregeneration

The audio pregeneration system reduces response latency by pre-generating common audio responses (greetings, threat responses) so they play instantly instead of waiting for a live AI response.

!!! tip "Why it matters"
 Without pregeneration, every greeting would require a full WebSocket round-trip to Google's servers. Pregeneration makes passing a citizen feel instant.

---

## How It Works

1. **Background generation** — The `PregenerationTaskService` runs scheduled tasks to generate audio for nearby citizens.
2. **Gemini client** — `PregenerationGeminiClient` handles the lightweight API calls for pregeneration.
3. **Caching** — Generated audio is cached per citizen, keyed by context.
4. **Playback** — When a player walks near a citizen, the `DeliveryInteractionManager` plays the appropriate pregenerated greeting instantly.
5. **Heatmap tracking** — The `PlayerHeatmapTracker` monitors where players spend time, prioritizing pregeneration for frequently visited areas.

---

## Greeting Types

| Type | Description | Config |
|------|-------------|--------|
| **Generic greetings** | Played when any player passes nearby | `pregeneratedGreetingDistance` |
| **Player-specific greetings** | Pregenerated for frequent citizen-player pairs | `enablePlayerGreetingPregen` |
| **Threat responses** | Pre-generated threat audio with configurable cooldown | `threatPlayCooldownMs` |

---

## Key Config Options

| Key | Default | Description |
|-----|---------|-------------|
| `enablePregeneration` | `true` | Enable audio pregeneration |
| `pregeneratedGreetingDistance` | `6.0` | Distance to trigger pregenerated greeting |
| `threatPlayCooldownMs` | `15000` | Cooldown between threat plays (ms) |
| `maxPregeneratedGreetingsPerCitizen` | `5` | Max stored greetings per citizen |
| `maxGreetingsPerTickInterval` | `1` | Max greetings per tick |
| `enablePlayerGreetingPregen` | `true` | Player-specific greeting pregeneration |
| `playerGreetingDistance` | `8.0` | Distance for player-specific greeting |
