# AI Provider Architecture Plan

## Overview

Replace the hard-coded Gemini dependency in Talking Colonists with a modular provider registry. Separate libraries register implementations for STT, LLM, and TTS capabilities. Users mix and match via presets.

---

## Module Structure (no circular deps)

```
mc-talking-api (plain Java Gradle module, published to sshcrack maven)
  (AiRegistry, AiProvider, BundledSession, ToolCallHandler,
   SttProvider, LlmProvider, TtsProvider, Capability,
   PresetDefinition, AudioChunk, migrated CitizenPromptView records)

  ↑ dependencies (compile)

  ├── gemini-live-library (Minecraft @Mod)
  │     implements BundledSession as GeminiLiveSession
  │     implements LlmProvider wrapping GeminiFlash
  │     implements TtsProvider wrapping GeminiTTS
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
        selects active preset from McTalkingConfig
        feeds BundledSession audio to AiAudioPlayer (ex-GeminiStream)
        jars mc-talking-api classes (no relocation, user installs single JAR)
        Runtime deps: mc-talking-api, gemini-live-library, local-ai-library (optional)
```

### No circular dependencies

- `mc-talking-api` depends on nothing but standard Java
- Provider libraries depend on `mc-talking-api` as `compileOnly` (classes at runtime come from talking-colonists)
- Talking-colonists depends on provider libraries at runtime
- Provider libraries declare `mandatory = true` dependency on `mc_talking` mod in their `mods.toml`
- **gemini-live-library is a mandatory dependency** of talking-colonists — it's auto-downloaded and always present. The default preset is `gemini_live_live`, so new users get Gemini Live out of the box.
- local-ai-library and future provider libraries are optional — discovered only if installed

---

## API Artifact (`mc-talking-api`)

### Key Interfaces

| Type | Package | Description |
|------|---------|-------------|
| `AiProvider` | `api.provider` | Base: `id()`, `displayName()`, `capabilities()`, `isBundled()` |
| `SttProvider extends AiProvider` | `api.provider` | `CompletableFuture<String> transcribe(short[] audio, int sampleRate)` |
| `LlmProvider extends AiProvider` | `api.provider` | `CompletableFuture<String> generate(String prompt, LlmConfig config)` |
| `TtsProvider extends AiProvider` | `api.provider` | `CompletableFuture<AudioChunk> synthesize(String text, TtsConfig config)` |
| `BundledAiProvider extends AiProvider` | `api.provider` | Factory: `BundledSession createSession(SessionConfig config, int priority)` |
| `BundledSession` | `api.session` | Bidirectional live AI session (STT+LLM+TTS as one unit) |
| `ToolCallHandler` | `api.session` | `@FunctionalInterface JsonElement onToolCall(id, name, args)` |
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

    // Tool responses
    void respondToToolCall(String toolCallId, JsonElement result);

    // Lifecycle
    void start();
    void close();
    boolean isActive();
}
```

### PresetDefinition

```java
public record PresetDefinition(
    String id,
    String displayName,
    @Nullable String sourceModId,       // null = user custom
    PresetMode mode,                    // BUNDLED or PIPELINE
    @Nullable String bundledProviderId, // when mode = BUNDLED
    @Nullable String sttProviderId,     // when mode = PIPELINE
    @Nullable String llmProviderId,
    @Nullable String ttsProviderId,
    Map<String, JsonElement> providerConfigs
)
```

### AiRegistry

```java
public class AiRegistry {
    static void registerProvider(AiProvider provider);
    static void registerPreset(PresetDefinition preset);
    static List<AiProvider> getProviders(Capability cap);
    static BundledAiProvider getBundledProvider(String id);
    static List<PresetDefinition> getPresets();
    static PresetDefinition getPreset(String id);
    static void addCustomPreset(PresetDefinition preset);
}
```

---

## Phase 0: `mc-talking-api` module

- Create plain Java Gradle module in the talking-colonists monorepo
- No Minecraft imports, no Stonecutter, no conditional compilation
- Publish to `https://maven.sshcrack.me/releases`
- Migrate existing `CitizenPromptView` and related view records from talking-colonists source into the API module
- Talking-colonists includes via `implementation(project(":mc-talking-api"))` — class files merged into output JAR
- Provider libs use `compileOnly` from Maven; runtime classes come from talking-colonists

---

## Phase 1: Refactor `gemini-live-library`

- Add `compileOnly` dependency on `mc-talking-api`
- Implement `GeminiLiveSession implements BundledSession` — wraps `GeminiLiveClient` WebSocket lifecycle, maps Gemini-specific callbacks to generic `BundledSession` callbacks
- Implement `GeminiFlashLlmProvider implements LlmProvider` — wraps `GeminiFlash.sendFlashRequest()`
- Implement `GeminiTtsProvider implements TtsProvider` — wraps `GeminiTTS.streamGenerateAudioConversation()`
- Implement `GeminiConnectionPool` — transport-level concurrency management:
  - Max concurrent sessions (configurable)
  - Priority-based eviction: `createSession(config, int priority)` atomically evicts lowest-priority session at capacity
  - Evicted session receives `onEvicted()` callback
- Register in `@Mod` constructor:
  - `gemini_live` → `BundledAiProvider`
  - `gemini_flash` → `LlmProvider`
  - `gemini_tts` → `TtsProvider`
- Register presets:
  - `gemini_live_live` — "Gemini Live" (bundled, real-time)
  - `gemini_live_flash_tts` — "Gemini Flash + TTS" (pregenerated conversations)
- Old `GeminiLiveClient` class stays untouched for backward compat

---

## Phase 2: Create `local-ai-library`

- New Stonecutter multi-loader mod (same pattern as gemini-live-library)
- `compileOnly` dependency on `mc-talking-api`
- Provider wrappers:

| Class | Implements | Backend |
|-------|-----------|---------|
| `WhisperProvider` | `SttProvider` | whisper-jni (tiny/base/small/medium/large models) |
| `OllamaProvider` | `LlmProvider` | ollama4j HTTP client, OpenAI-compatible |
| `PiperProvider` | `TtsProvider` | piper-jni or CLI subprocess fallback |
| `LocalPipelineSession` | `BundledSession` | Composes whisper→ollama→piper sequentially |

- Each provider uses internal `ExecutorService` for blocking calls — callbacks fire on executor threads, consumer must thread-safely handle results
- `LocalPipelineSession` flow:
  ```
  sendAudio() → Whisper STT → onStt (transcription)
    → prompt forwarded to Ollama LLM
    → generated text forwarded to Piper TTS
    → onAudio (PCM chunks)
  ```
- Register in `@Mod` constructor: individual providers + `local_pipeline` `BundledAiProvider`
- Register presets: `local_fast` (whisper-tiny + llama3.2:1b + piper-low), `local_quality` (whisper-medium + llama3.1:8b + piper-medium)

---

## Phase 3: Migrate Talking Colonists

- `AiAudioPlayer` (renamed `GeminiStream`) — Voice Chat playback pipeline stays in talking-colonists, fed by `BundledSession.onAudio()` callback
- Replace `GeminiWsClient extends GeminiLiveClient` with a new base class that wraps `BundledSession` — all reconnect/queue/session-state logic that is Gemini-specific either moves to the provider layer or becomes generic
- `McTalkingConfig` changes:
  - Replace `AvailableAI`, `ConversationMode`, `MemoryMode` enums with `activePreset: String`
  - Add `customPresets: String` (JSON-serialized `List<PresetDefinition>`)
  - Keep `geminiApiKey` for Gemini-specific preset configs
- `ConversationManager` slot system stays for application-level concurrency (range checks, priority tiers — player=100, mumble/c2c=50, background=10)
- Provider-level concurrency is handled internally by each library's connection pool

---

## Config/UX

- **Preset selector** — user picks from library-registered presets + custom presets in the YACL config screen
- **Custom preset** — user selects STT/LLM/TTS provider per slot, sets model options in a raw JSON field (or list UI)
- **Fallback** — if a provider fails (e.g. Ollama not running, Piper binary missing), the session fires `onError`. Talking Colonists falls back to the default preset or shows an in-game chat warning
- **Active preset** stored as a simple String in config, switching takes effect on next conversation

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Provider discovery | Manual `AiRegistry.register()` in `@Mod` constructor | Proven pattern (CitizenPromptService), avoids SPI classloader issues on NeoForge |
| Bundled vs composable | Both — `BundledSession` + `SttProvider`/`LlmProvider`/`TtsProvider` | Gemini Live is inherently bundled; local providers are composable. Registry supports both. |
| Concurrency model | Provider manages its own pool + priority eviction | Talking Colonists' slot system is a workaround for Gemini's limits. The provider should own this. |
| Threading | Provider owns executor, callbacks fire on provider threads | Clean API, consumer is responsible for thread-safe game state access |
| Config storage | JSON string field in YACL config | YACL doesn't support dynamic fields; a serialized JSON string is simple and works |
| Preset lifetime | `sourceModId` field; absent-library presets filtered out at startup | Prevents config breakage when a provider mod is removed |
| Audio playback | Stays in Talking Colonists (`AiAudioPlayer`) | Tightly coupled to Simple Voice Chat API; provider libraries shouldn't depend on it |

## Risks

| Risk | Mitigation |
|------|-----------|
| Bundled vs composable interface mismatch | `onToolCall`/`onInterrupted` are optional callbacks; pipeline sessions never invoke them |
| Latency stacking in pipeline | Pipeline mode targets pregenerated (c2c) conversations, not real-time. User selects via preset. |
| Thread safety — callbacks on provider threads | Follow existing GeminiStream pattern: buffered queues consumed on server tick |
| Version skew of mc-talking-api | Thin stable interfaces + semantic versioning on the API artifact |
| Config orphaned on library removal | `sourceModId` filtering + logged warnings on startup |
