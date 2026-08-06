# AI Provider Architecture — Implementation Summary

## What was implemented

### `:api` subproject (talking-colonists/api/)
Pure Java 17+ subproject with zero Minecraft/Forge/NeoForge dependencies.
- `AiProvider` — base interface with `providerId()`, `displayName()`, `capabilities()`
- `Capability` — enum `STT`, `LLM`, `TTS`, `LIVE_BUNDLE`
- `SttProvider` — `CompletableFuture<String> transcribe(AudioData)`
- `LlmProvider` — `CompletableFuture<LlmResponse> generate(LlmRequest)`
- `TtsProvider` — `CompletableFuture<AudioData> synthesize(TtsRequest)`
- `LiveSession` / `LiveSessionListener` / `LiveSessionConfig` / `LiveSessionProvider` — live bundle session contract
- `ToolDefinition` / `ToolCall` — shared function-calling shapes
- `AiProviderRegistry` — static thread-safe registry with `register()`/`getProvider()`/`getProviders()`
- `ProviderSelection` — config POJO with `validate()` fallback logic
- `ProviderHealth` / `AudioData` / `LlmRequest` / `LlmResponse` / `TtsRequest` — value types

### Build system
- `settings.gradle.kts`: `include("api")` added
- `build.neoforge.gradle.kts` / `build.forge.gradle.kts`: `implementation(project(":api"))`, `gemini_live_lib` → `compileOnly`
- `platform` dependency: `gemini_live_lib` changed from `required` → `optional`
- `AiProviderRegistry` now stores `CONFIG_GEMINI_API_KEY` for provider-agnostic config key passing

### gemini-live-library adapter classes
- `GeminiLiveSession` implements `LiveSession` — wraps GeminiLiveClient via adapter pattern
- `GeminiLiveSessionProvider` implements `LiveSessionProvider` — creates sessions from registry
- `GeminiFlashLlm` implements `LlmProvider` — wraps `GeminiFlash.sendSimpleFlashRequest()`
- `GeminiTtsImpl` implements `TtsProvider` — wraps `GeminiTTS.streamGenerateAudioConversation()`
- `GeminiLiveLib` — registers all providers in `@Mod` constructor
- All providers read API key from `AiProviderRegistry.getConfig(CONFIG_GEMINI_API_KEY)`

### talking-colonists refactoring
- `GeminiGameSession` / `CitizenGameSession` — game-logic-only wrappers around `LiveSession`
- `LocalComposableSession` implements `LiveSession` — sequential STT→LLM→TTS pipeline
- `SessionFactory` — creates sessions from `ProviderSelection` via registry
- `McTalking.onCommonSetup()` — sets API key in registry

### local-ai-library (stub project at ../local-ai-library/)
- `WhisperStt` / `OllamaLlm` / `PiperTts` — stub implementations
- `LocalAiLib` — registers providers (not yet wired into composite build)

## Not yet implemented (deferred to follow-up PRs)

1. **Full ConversationManager refactoring** — current `ConversationManager` still uses old `GeminiWsClient`/`CitizenWsClient` directly. The new `SessionFactory` / `GeminiGameSession` classes are ready but not wired into the manager.
2. **Config UI provider selection** — dynamic dropdowns sourced from `AiProviderRegistry` at screen-open time.
3. **Background task migration** — `CitizenConversationGenerator`, memory compaction, pregeneration still use `GeminiFlash.sendSimpleFlashRequest()` directly.
4. **local-ai-library Stonecutter setup** — needs full multi-loader build system.
5. **end-to-end smoke tests** for all provider combinations.
