# API Settings

Configuration options for the Gemini API connection.

!!! warning "API Key Required"
 The mod will not function without a valid Gemini API key. Get one at [Google AI Studio](https://aistudio.google.com/apikey) - it's free.

---

## Configuration Options

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `geminiApiKey` | String | `""` | Google Gemini API key |
| `currentAiModel` | Enum | `Flash3` | AI model to use for conversations |

---

## Available Models

| Value | API Model | Free Tier Limit |
|-------|-----------|-----------------|
| `Flash3` | `gemini-3.1-flash-live-preview` | Up to **3 concurrent** connections |
| `Flash2_5` | `gemini-2.5-flash-native-audio-preview-12-2025` | Only **1 concurrent** connection |

!!! tip "Which model should I use?"
 - **Flash 3** for most users - faster and supports up to 3 simultaneous conversations.
 - **Flash 2.5** if you want to save API credits and only need 1 conversation at a time.

---

## Voice Options

Each model supports both male and female voices. The selected voice is randomized per conversation, giving each interaction a unique feel.
