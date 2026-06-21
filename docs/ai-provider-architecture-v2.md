# AI Provider Architecture v2

## Overview

Replace the hard-coded Gemini dependency in Talking Colonists with a modular provider registry. Separate libraries register implementations for STT, LLM, TTS, and bundled live sessions. Users mix and match via presets in a three-category config.

---

## Module Structure

```
mc-talking-api (plain Java Gradle module, published to sshcrack maven)
  (AiRegistry, AiProvider, BundledSession, ToolCallHandler,
   SttProvider, LlmProvider, TtsProvider, PregenerationProvider,
   Capability, PresetDefinition, AudioChunk, AudioFormat,
   VoiceDescriptor, Gender, ToolDefinition, QuotaManager, ConfigField)

  ↑ dependencies (compileOnly from Maven; classes JarJarred into talking-colonists)

  ├── gemini-live-library (Minecraft @Mod)
  │     implements BundledSession as GeminiLiveSession
  │     implements LlmProvider wrapping GeminiFlash
  │     implements TtsProvider wrapping GeminiTTS
  │     implements PregenerationProvider wrapping GeminiTTS multi-speaker
  │     registers via AiRegistry.register() in @Mod constructor
  │     Dependencies: mc-talking-api (compileOnly), WebSocket, Gson
  │
  ├── local-ai-library (Minecraft @Mod)
  │     implements SttProvider as WhisperProvider (whisper-jni)
  │     implements LlmProvider as OllamaProvider (ollama4j)
  │     implements TtsProvider as PiperProvider (piper-jni/CLI)
  │     implements BundledSession as LocalPipelineSession (composes all 3)
  │     registers individual providers + bundled pipeline in @Mod constructor
  │     Dependencies: mc-talking-api (compileOnly), whisper-jni, ollama4j, piper
  │
  └── talking-colonists (Minecraft @Mod)
        consumes providers via AiRegistry
        selects active presets from McTalkingConfig
        feeds BundledSession audio to AiAudioPlayer
        JarJars mc-talking-api classes into output JAR
        Runtime deps: gemini-live-library (mandatory), local-ai-library (optional)
```

### No circular dependencies

- `mc-talking-api` depends on nothing but standard Java + Gson
- Provider libraries depend on `mc-talking-api` as `compileOnly` (classes at runtime come from talking-colonists' JarJar)
- Talking-colonists depends on provider libraries at runtime
- Provider libraries declare `mandatory = true` dependency on `mc_talking` mod in their `mods.toml`
- **gemini-live-library is a mandatory dependency** of talking-colonists — always present. The default preset is `gemini_live.live_live`.
- local-ai-library and future provider libraries are optional — discovered only if installed

### JarJar Strategy

`mc-talking-api` classes are JarJarred (shadowed) into the talking-colonists output JAR. No relocation needed (API namespace is unique). Provider libs use `compileOnly` from Maven. This prevents classpath conflicts if a provider JAR was built against a different API version.

---

## API Artifact (`mc-talking-api`)

### Key Interfaces

| Type | Package | Description |
|------|---------|-------------|
| `AiProvider` | `api.provider` | Base: `id()`, `displayName()`, `capabilities()`, `availableVoices()` |
| `SttProvider extends AiProvider` | `api.provider` | `CompletableFuture<String> transcribe(short[] audio, int sampleRate)` |
| `LlmProvider extends AiProvider` | `api.provider` | `CompletableFuture<String> generate(String prompt, LlmConfig config)` |
| `TtsProvider extends AiProvider` | `api.provider` | `CompletableFuture<AudioChunk> synthesize(String text, TtsConfig config)` |
| `BundledAiProvider extends AiProvider` | `api.provider` | Factory: `BundledSession createSession(SessionConfig config)` |
| `PregenerationProvider extends AiProvider` | `api.provider` | `CompletableFuture<PregeneratedConversation> generateConversation(ConversationScript script, PregenerationConfig config)` |
| `BundledSession` | `api.session` | Bidirectional live AI session (STT+LLM+TTS as one unit) |
| `ToolCallHandler` | `api.session` | `@FunctionalInterface JsonElement onToolCall(String id, String name, JsonObject args)` |
| `AiRegistry` | `api.provider` | Static registry: `register()`, `getProviders()`, `getPresets()` |

### BundledSession

```java
public interface BundledSession {
    // Input
    void sendAudio(short[] pcmData, int sampleRate);
    void sendText(String text);
    void interrupt();

    // Output callbacks (set before start)
    BundledSession onText(Consumer<String> handler);
    BundledSession onAudio(Consumer<AudioChunk> handler);
    BundledSession onStt(Consumer<String> handler);
    BundledSession onTurnComplete(Runnable handler);
    BundledSession onError(Consumer<Throwable> handler);
    BundledSession onToolCall(ToolCallHandler handler);
    BundledSession onQuotaExceeded(Runnable handler);

    // Tool responses
    void respondToToolCall(String toolCallId, JsonElement result);

    // Lifecycle
    void start();
    void close();
    boolean isActive();
}
```

- `sendText()` skips STT in pipeline sessions (e.g. LocalPipelineSession goes directly to Ollama)
- Callbacks fire on provider-owned executor threads. Consumer must handle thread-safe game state access.
- `PregenerationProvider` uses the same pattern but is one-shot: `generateConversation()` → single `AudioChunk` result.

### AudioChunk & AudioFormat

```java
public record AudioFormat(int sampleRate, int sampleSizeInBits, boolean signed, boolean bigEndian) {
    public static final AudioFormat DEFAULT_PCM = new AudioFormat(16000, 16, true, false);
}

public record AudioChunk(byte[] data, AudioFormat format) {}
```

### VoiceDescriptor & Gender

```java
public enum Gender { MALE, FEMALE }

public record VoiceDescriptor(
    String id,
    String displayName,
    Gender gender,
    float pitchFactor,
    @Nullable String language,
    Map<String, JsonElement> extra
)
```

Providers expose a list of available voices via `AiProvider.availableVoices()`. Voice selection is deterministic: providers select a voice from `availableVoices()` using `new Random(citizenSeed)` filtered by gender. Pitch factor is applied by talking-colonists' `AiAudioPlayer`.

### PregenerationProvider

```java
public record ConversationScript(List<DialogueLine> lines) {}
public record DialogueLine(String speakerName, String text, @Nullable String voiceId) {}
public record PregeneratedConversation(AudioChunk audio) {}
public record PregenerationConfig(@Nullable String model, Map<String, JsonElement> extra) {}

public interface PregenerationProvider extends AiProvider {
    CompletableFuture<PregeneratedConversation> generateConversation(
        ConversationScript script, PregenerationConfig config
    );
}
```

The Gemini provider wraps `GeminiTTS.streamGenerateAudioConversation()` (streams chunks, assembles into single `AudioChunk`). Local providers could use Piper TTS on the rendered text.

### ToolDefinition

```java
public record ToolDefinition(
    String name,
    String description,
    @Nullable JsonObject parameters  // JSON Schema
) {}

public record LlmConfig(
    @Nullable String systemPrompt,
    @Nullable List<ToolDefinition> tools,
    @Nullable ToolCallHandler toolHandler,
    @Nullable String model,
    Map<String, JsonElement> extra
)
```

The `SessionConfig` and `LlmConfig` carry `List<ToolDefinition>` in portable JSON Schema format. Each provider internally converts to its native format (Gemini `Property` hierarchy, OpenAI JSON Schema for Ollama, etc.). No converter interface in the API — providers own this conversion.

### PresetDefinition

```java
public record PresetDefinition(
    String id,                    // e.g. "live_live"
    String displayName,
    @Nullable String sourceModId, // null = user custom preset (e.g. "gemini_live")
    PresetMode mode,              // BUNDLED or PIPELINE
    @Nullable String bundledProviderId,
    @Nullable String sttProviderId,
    @Nullable String llmProviderId,
    @Nullable String ttsProviderId,
    @Nullable String pregenerationProviderId,
    Map<String, JsonElement> providerConfigs
) {
    public String fullId() {
        return sourceModId != null ? sourceModId + "." + id : id;
    }
}
```

Presets with `sourceModId` are filtered out at startup if the mod isn't loaded. Custom presets (`sourceModId = null`) survive mod removal.

### QuotaManager

```java
@FunctionalInterface
public interface QuotaManager {
    boolean tryConsume(String resourceKey);
    default void reportSuccess() {}
    default void reportFailure(Throwable error) {}
}
```

Each provider library registers its own `QuotaManager` with `AiRegistry`. The Gemini provider ships `GeminiQuotaManager` (3 concurrent for free tier, configurable). The local-ai-library ships a no-op. Talking-colonists pipes `onQuotaExceeded` from `BundledSession` into `ConversationManager` fallback logic.

### ConfigField (for dynamic YACL UI)

```java
public record ConfigField(
    String key,            // dot-separated, e.g. "apiKey"
    ConfigType type,       // STRING, INTEGER, DOUBLE, BOOLEAN
    @Nullable Object defaultValue,
    int min, int max       // 0 = no limit for numeric types
)

public enum ConfigType { STRING, INTEGER, DOUBLE, BOOLEAN }
```

Translation keys follow the pattern:
- `mc_talking.config.provider.<modId>.<key>` — display name
- `mc_talking.config.provider.<modId>.<key>.desc` — tooltip

Lang entries ship in each provider library's `assets/<modid>/lang/en_us.json`.

---

## Config Changes (`McTalkingConfig`)

### Deleted fields

- `currentAiModel: AvailableAI`
- `conversationMode: ConversationMode`
- `memoryMode: MemoryMode`
- `geminiApiKey: String`

### New fields

```java
// Preset selectors per conversation category, with optional fallback
@SerialEntry public String livePreset = "gemini_live.live_live";
@SerialEntry public String liveFallback = "";

@SerialEntry public String pregeneratedPreset = "gemini_live.flash_tts";
@SerialEntry public String pregeneratedFallback = "gemini_live.live_live";

@SerialEntry public String backgroundPreset = "gemini_live.flash";
@SerialEntry public String backgroundFallback = "";

// Provider-specific settings (JSON blob)
@SerialEntry public String providerConfig = "{}";

@SerialEntry public int configVersion = 4;
```

### Migration (`configVersion < 4`)

| Old config | New config |
|-----------|------------|
| `geminiApiKey` | Moved into `providerConfig` JSON: `{"gemini_live":{"apiKey":"..."}}` |
| `currentAiModel` + `conversationMode` = `LIVE_WEBSOCKETS` | `livePreset = "gemini_live.live_live"` |
| `currentAiModel` + `conversationMode` = `FLASH_TTS`/`AUTO` | `pregeneratedPreset = "gemini_live.flash_tts"`, `pregeneratedFallback = "gemini_live.live_live"` |
| `memoryMode` = `LIVE` | `backgroundPreset = "gemini_live.live"` |
| `memoryMode` = `FLASH` | `backgroundPreset = "gemini_live.flash"` |

All other fields (language, interaction, citizen-to-citizen toggles, pregen, rumor mill, broadcast, etc.) remain unchanged.

### Fallback logic

Handled in `ConversationManager`:
1. Try primary preset's provider
2. If it fails with quota exceeded / unavailable, try the fallback preset
3. If fallback also fails, warn the player

---

## Voice System

Voice selection is deterministic per citizen, handled by each provider library:

1. Taling-colonists passes `citizenSeed` (UUID bits XORed) + `isFemale` in the session config
2. The provider filters its `availableVoices()` by gender
3. The provider picks: `voices.get(new Random(seed).nextInt(voices.size()))`
4. The selected `VoiceDescriptor` is returned in session metadata
5. Talking-colonists' `AiAudioPlayer` applies `pitchFactor` from the descriptor

---

## Concurrency Model

**Application level** (`ConversationManager`):
- Slot system with priority tiers: player=100, mumble/c2c=50, background=10
- Player conversations always succeed (evict lowest-priority non-player session)
- Fallback retry logic

**Provider level** (e.g. `GeminiConnectionPool`):
- Connection pool with max concurrent sessions (configurable, default 3)
- Priority-based eviction at capacity
- `onEvicted()` callback on evicted sessions
- Quota tracking via `QuotaManager`

**Threading**:
- Providers own their executor/thread pools
- All `BundledSession` callbacks fire on provider threads
- Talking-colonists queues audio output and processes on server tick (same pattern as `GeminiStream`)

---

## Phase 0: `mc-talking-api` module

- Create plain Java Gradle module in the talking-colonists monorepo
- No Minecraft imports, no Stonecutter, no conditional compilation
- Only dependency: Gson (for `JsonElement`/`JsonObject`)
- JarJar into talking-colonists output JAR (via `shadow` plugin)
- Publish artifact to `https://maven.sshcrack.me/releases` for provider libs
- Talking-colonists uses `implementation(project(":mc-talking-api"))` (classes JarJarred)
- Provider libs use `compileOnly` from Maven

## Phase 1: Refactor `gemini-live-library`

- Add `compileOnly` dependency on `mc-talking-api`
- Implement `GeminiLiveSession implements BundledSession` — wraps `GeminiLiveClient` WebSocket lifecycle, maps Gemini-specific callbacks to generic `BundledSession` callbacks
- Implement `GeminiFlashProvider implements LlmProvider` — wraps `GeminiFlash.sendFlashRequest()`
- Implement `GeminiTtsProvider implements TtsProvider` — wraps `GeminiTTS.streamGenerateAudioConversation()`
- Implement `GeminiPregenerationProvider implements PregenerationProvider` — wraps multi-speaker Gemini TTS
- Implement `GeminiConnectionPool` — transport-level concurrency:
  - Max concurrent sessions (configurable, default 3)
  - Priority-based eviction at capacity
  - Evicted session receives `onEvicted()` callback
- Implement `GeminiQuotaManager implements QuotaManager`
- Register in `@Mod` constructor: all providers + presets
- Old `GeminiLiveClient` class stays for backward compat (in-transit)

### Registered presets

| ID | Name | Mode |
|----|------|------|
| `gemini_live.live_live` | Gemini Live | BUNDLED |
| `gemini_live.flash_tts` | Gemini Flash + TTS | PIPELINE (Flash→TTS) |
| `gemini_live.flash` | Gemini Flash | PIPELINE (LLM only) |

## Phase 2: Create `local-ai-library`

- New Stonecutter multi-loader mod (same pattern as gemini-live-library)
- `compileOnly` dependency on `mc-talking-api`
- Provider wrappers for Whisper, Ollama, Piper
- `LocalPipelineSession` — `sendAudio()` → Whisper STT → Ollama LLM → Piper TTS → `onAudio()`
- `sendText()` skips Whisper stage
- Each stage runs on executor: `sendAudio()` submits a future chain
- `interrupt()` cancels remaining futures
- Registers presets: `local.fast`, `local.quality`

## Phase 3: Migrate Talking Colonists

1. **Create adapter layer** — thin bridge classes implementing `BundledSession` by wrapping existing `GeminiWsClient`, validating the API before the library is refactored
2. **`AiAudioPlayer`** — renamed `GeminiStream`, stays in talking-colonists, fed by `BundledSession.onAudio()`. Applies pitch from `VoiceDescriptor.pitchFactor`
3. **`ConversationManager`** — replaces direct `new CitizenWsClient()` with `AiRegistry` lookups; implements fallback logic; slot system stays for application-level concurrency
4. **Config screen** — dynamic YACL UI built from registered `ConfigField`s + preset selectors
5. **Greeting pregen** → `BundledSession` (`sendText` → audio out)
6. **Memory compaction** → `LlmProvider` (text in, text out)

---

## Risks

| Risk | Mitigation |
|------|-----------|
| Bundled vs composable interface mismatch | `onToolCall`/`onInterrupted` are optional; pipeline sessions never invoke them |
| Latency stacking in pipeline | Pipeline mode targets pregenerated (c2c) conversations, not real-time |
| Thread safety — callbacks on provider threads | Follow existing GeminiStream pattern: buffered queues consumed on server tick |
| Version skew of mc-talking-api | Thin stable interfaces + semantic versioning on the API artifact |
| Config orphaned on library removal | `sourceModId` filtering + logged warnings on startup |
| JarJar shading conflicts | No relocation (unique namespace), but verify with `shadow` plugin validation |
