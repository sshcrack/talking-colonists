# AI backend interface (design, roadmap L1)

Status: exploratory design. Nothing here is implemented. This document answers #131 ("Local AI
Option"): what a backend seam would look like, which features degrade without Gemini Live audio,
and what the migration costs. It proposes no public API change.

## Goal and non-goals

**Goal:** make it possible, later, to run citizens on something other than Google Gemini, for
example a local pipeline of Whisper (speech-to-text) + a local LLM (Ollama or llama.cpp) + Piper
(text-to-speech). This could be done in core or as an addon, and different parts could be mixed
(local TTS with Gemini text, for example).

**Non-goals for this task:**
- Implementing a local backend.
- Changing the addon API.
- Changing Gemini behaviour.

## What talks to Gemini today

Four capabilities, reached through the `me.sshcrack.gemini_live_lib` library and one direct HTTP
call:

| Capability | Where | Library surface |
|---|---|---|
| **Live audio sessions** (speech in, speech out, tools, transcripts) | `GeminiWsClient` (1.4k lines) and its subclasses `CitizenWsClient` (player, mumble, urgent contact, addon ambient, controlled turns), `PregenerationGeminiClient` (pregenerated greetings), `MemoryCompactionWsClient` (text-only Live) | `GeminiLiveClient` (WebSocket), `BidiGenerateContentSetup`, `RealtimeInput`, `ClientMessages` |
| **Text generation** (plain and JSON) | `TextGenerationRuntime` (addon text, A3), `CitizenConversationGenerator` (pair scripts), `CitizenMemoryGenerator`, `PlayerConversationMemoryGenerator`, `MemoryCompactionService` / `MemoryStructuredOutput` (structured memory) | `GeminiFlash.sendFlashRequest` with `GenerateContentRequest` |
| **Speech synthesis** (multi-speaker) | `CitizenConversationGenerator` FLASH_TTS path, `TtsQuotaManager`, `TtsVoiceRecovery` | `GeminiTTS.streamGenerateAudioConversation` |
| **Transcription** (audio in, text out) | `GeminiAudioTranscriber` (player speech capture, A10) | direct `generateContent` HTTP call |

Other parts of the code depend on these capabilities through Gemini-specific pieces:

- **Tool schemas:** addon tools are declared with the neutral `AiToolParameter` API. `AiToolSchemaAdapter`
  converts them (and built-in tools) into the library's `Property` types for the Live setup message.
  Tool calls come back through `onFunctionCall(name, JsonObject)`.
- **Quota:** `QuotaTracker` keys on model name strings and `QuotaRetryInfo` parses Google error
  bodies. A7's `ProviderBudgetView` already exposes this neutrally (`ProviderQuotaState` OK /
  EXHAUSTED / UNKNOWN, slot usage).
- **Voices:** `VoiceSelectionService` picks a Gemini prebuilt voice name per citizen (deterministic
  from UUID and gender) and remembers voices a model rejected.
- **Config:** `AvailableAI` lists Gemini Live models. `FLASH_MODEL` and `TTS_MODEL` are constants.
  `ConversationMode` (LIVE_WEBSOCKETS / FLASH_TTS / AUTO) chooses how pair conversations are made.
- **Audio path (already backend-neutral):**
  - Microphone: Simple Voice Chat Opus is decoded to 48 kHz PCM, then goes to `MicrophoneTurnModule`
    (speech detection, barge-in) and on to the provider.
  - Playback: provider PCM at any sample rate goes through the `Sonic` pitch/rate changes, then the
    turn gate, then Simple Voice Chat.

Already provider-neutral, with no change needed: prompts (`CitizenPromptView`, contributors, the
prompt snapshot), memory storage, tools' game-side execution, conversation lifecycle and
ownership, the ambient speech budget, the addon API, and the playback and microphone modules.

## Proposed minimum interface

All internal (`me.sshcrack.mc_talking.internal.ai`), one interface per capability. A backend
implements any subset, and the config picks a backend per capability.

```java
/** A realtime spoken conversation with one citizen. */
interface LiveSession extends AutoCloseable {
    void sendAudio(short[] pcm48k);           // microphone frames (already speech-gated)
    void sendText(String userTurn);            // typed text (A4) and system prompts
    void sendContextNote(String note);         // after the current turn (A4)
    void cancelResponse();                     // barge-in: stop generating the current answer
    void close();
}

interface LiveSessionFactory {
    LiveCapabilities capabilities();
    LiveSession open(LiveSessionSpec spec, LiveSessionListener listener);
}

record LiveSessionSpec(String systemPrompt, List<ToolSpec> tools, VoiceSpec voice, String language,
                       Set<Modality> output, @Nullable String resumeToken) {}

interface LiveSessionListener {
    void onReady();
    void onAudio(UUID turnId, short[] pcm, int sampleRate);      // playback
    void onOutputText(UUID turnId, String chunk);                 // transcript or text modality
    void onInputTranscript(String chunk);                         // what the player said
    void onTurnComplete(UUID turnId);
    void onInterrupted(UUID turnId);
    CompletableFuture<JsonObject> onToolCall(String tool, JsonObject args);
    void onResumeToken(String token);                             // optional
    void onQuotaExceeded(@Nullable Duration retryAfter);
    void onClosed(CloseReason reason);                            // NORMAL, RECOVERABLE, FATAL
}

record LiveCapabilities(boolean serverTurnDetection, boolean nativeBargeIn, boolean resumption,
                        boolean inputTranscription, boolean toolCalls, List<String> voices) {}

/** One-shot text, plain or JSON-schema constrained. */
interface TextBackend {
    CompletableFuture<String> generate(TextSpec spec);   // spec: system, prompt, schema?, limits
}

/** Speech synthesis, one line or a multi-speaker script. */
interface SpeechBackend {
    void synthesize(List<ScriptLine> lines, Consumer<AudioChunk> out) throws Exception;
    List<VoiceInfo> voices(String language);
}

/** Speech-to-text for captured player audio (A10). */
interface TranscriptionBackend {
    CompletableFuture<String> transcribe(short[] pcm48k, String languageHint);
}
```

`ToolSpec` is a name, a description and an `AiToolParameter`-style schema. It already exists in
neutral form in the addon API, so the Gemini adapter keeps doing the conversion it does today.
Every backend reports capacity and quota as a `ProviderBudgetView` slice (A7), so the
budget/quota API addons already use stays the same. A local backend reports
`ProviderQuotaState.OK` and slots equal to its concurrency, which is usually 1 per GPU.

### Why these seams

- `TextGenerationRuntime.Transport` and A10's `SpeechCaptureRuntime.Transcriber` are already
  one-method seams. They take a Gemini request type and PCM respectively. Making the text
  transport take a `TextSpec` is a small, local change.
- TTS has one call site, the pair-script pipeline.
- The Live session is the hard part. `GeminiWsClient` **inherits** from the library's WebSocket
  client, so protocol handling and game-side behaviour live in one class hierarchy. These
  behaviours must be split out and kept, not duplicated per backend:
  - playback turns and the drain gate
  - the microphone turn module
  - transcript and utterance tracking
  - tool dispatch
  - presentation status (thinking, speaking)
  - reconnect policy

## A local backend as a cascaded pipeline

Gemini Live is one model that listens and speaks. A local backend chains three:

```
mic PCM ─► MicrophoneTurnModule (end of speech) ─► Whisper ─► LLM (streaming, tools) ─►
      sentence splitter ─► Piper per sentence ─► Sonic ─► Simple Voice Chat
```

- **End of turn:** Gemini detects it on the server. Locally, the existing `MicrophoneTurnModule`
  closes a turn after a silence gap. That is the same signal A10 uses for speech capture.
- **Streaming:** sentence-chunked TTS starts speaking after the first LLM sentence instead of the
  whole answer. Expect about 1–3 s to first audio on a consumer GPU, against about 0.5–1 s for
  Gemini Live.
- **Tools:** Ollama and llama.cpp server expose OpenAI-style tool calling. `ToolSpec` maps to it
  directly, but a small model calls tools less reliably. The existing guardrails still apply:
  confirmation tools, scope and permission checks, typed outcomes.
- **Structured output (memory):** Ollama's `format` accepts a JSON schema. Other runtimes may need
  "reply with JSON" prompting plus validation. `MemoryStructuredOutput` already validates.

## What degrades without Gemini Live audio

| Feature | Gemini Live | Cascaded local pipeline |
|---|---|---|
| Barge-in | Server VAD interrupts generation natively; local detection also cuts playback | Local detection cuts playback (works today), and `cancelResponse()` must also abort the LLM and TTS streams. Coarser: an interruption lands at sentence granularity |
| Turn detection | Server VAD with semantic end-of-turn | Silence gap only. Pauses mid-sentence can end a turn early; tune the gap (A10 uses 0.9 s) |
| Latency | ~0.5–1 s to first audio | ~1–3 s (STT + first LLM sentence + first TTS chunk), hardware-bound |
| Voice consistency | Stable prebuilt voice per citizen | Stable if the voice is chosen deterministically from the UUID among installed Piper voices (same rule as `VoiceSelectionService`). There are far fewer voices, so neighbours share voices more often. Prosody is flatter |
| Emotion and tone | Model speaks with prosody that follows the text | TTS reads the text neutrally. Pitch/age changes via `Sonic` still apply |
| Session resumption | Resume token across reconnects | None. Rebuild context from the system prompt plus memory (already summarised) and the recent transcript |
| Transcripts (memory, utterance events) | Input/output transcription streams | Free, since STT and the LLM produce text anyway |
| Multi-speaker pair conversations | FLASH_TTS script + multi-speaker TTS, or two Live sessions | Easier: script from the LLM, then per-line Piper voices. No 10-requests-a-day TTS limit |
| Tool calling | Reliable | Model-dependent. Keep confirmation tools and typed outcomes |
| Languages | Many, one model | Whisper is multilingual; Piper needs a voice per language; small LLMs are weaker outside English |
| Quota | Free-tier limits (3 Live sessions, 10 TTS a day) | No quota, but concurrency is bound by GPU memory, usually 1–2 sessions |

Nothing in gameplay depends on Live-only features. Prompts, memory, tools, lifecycle and the addon
API are all above the seam.

## Migration steps and cost

| Step | Work | Size | Risk |
|---|---|---|---|
| 1 | `TextBackend`: `TextGenerationRuntime.Transport` and the memory generators take a `TextSpec`; a Gemini adapter builds `GenerateContentRequest` | S | Low. Covered by prompt snapshot and text runtime tests |
| 2 | `TranscriptionBackend`: rename A10's `Transcriber`, move it under `internal.ai` | XS | None |
| 3 | `SpeechBackend`: wrap the single `GeminiTTS` call in `CitizenConversationGenerator`; voices through `VoiceSelectionService` per backend | S | Low |
| 4 | `LiveSession`: split `GeminiWsClient` into a game-side session (playback, turns, microphone, transcripts, tools, presentation, recovery policy) and a `GeminiLiveTransport` that owns the WebSocket and protocol. `CitizenWsClient`, `PregenerationGeminiClient` and `MemoryCompactionWsClient` move onto the session | L–XL | High. This is the core of every conversation. Do it with the #116 refactor, behind the existing GameTests and client smoke test, in small PRs |
| 5 | Config: backend choice per capability; `AvailableAI` stays the Gemini model list; `ConversationMode` gets a "script + TTS" meaning that is backend-neutral | S | Low (config migration) |
| 6 | Local backend (Whisper + Ollama + Piper) | XL | Separate project |

Steps 1–3 are cheap and useful on their own (for example, OpenAI-compatible text for memory
while keeping Gemini Live). Step 4 is where the cost is, and it overlaps with #116's plan to
decompose `GeminiWsClient`.

## Packaging

The local pipeline needs native libraries (whisper.cpp, Piper/onnxruntime) or external servers.
Keeping those out of the core jar suggests an **addon mod** that provides backends. That requires
a public backend SPI. The options:

1. **Internal SPI first (recommended):** do steps 1–5 internally, ship a local backend in core
   only if it can talk to external servers over HTTP (Ollama, a Whisper server, a Piper server),
   which needs no native code. Publish the SPI in a later API minor once it is stable.
2. **Public SPI now:** lets an addon ship native backends sooner, but freezes a young interface
   under the API compatibility policy (additive-only), which is costly while step 4 is still
   moving.

Server-side processing is already the model. All requests are made by the server with the
server's key or backend, so "make the server handle all AI requests" from #131 is how the mod
works today.

## Recommendation

1. Do steps 1–3 when next touching text, TTS or transcription code. They are small and
   de-risk the rest.
2. Fold step 4 into #116's `GeminiWsClient` decomposition instead of doing it twice.
3. Prototype a local backend against external HTTP servers (Ollama's OpenAI-compatible
   endpoint, a Whisper server, a Piper HTTP server) behind the internal SPI before committing to a
   public SPI or native bundling.

## Open questions

- Should mixing be allowed inside one conversation (local STT + Gemini text + local TTS), or only
  per capability (all Live sessions on one backend)? Per capability is much simpler.
- Minimum acceptable latency for a player conversation before the local path should warn.
- Whether `ModalityModes.TEXT` should become the default for local backends on weak hardware.
