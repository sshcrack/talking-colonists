# Connection and state-machine review — 2026-09-07

Reviewed Talking Colonists `ccb4426` and the sibling Gemini Live Library `c7b6d94`.
The findings below describe the original baseline. Runtime fixes and automated
regressions were subsequently implemented on 2026-09-08 in both repositories.
Line references and “fix direction” paragraphs below are historical review evidence,
not descriptions of the fixed tree. This is not a guarantee that every bug is eliminated.

## Implementation follow-up — 2026-09-08

All nine findings below have fixes and offline regression coverage:

| Failure | Implemented protection |
| --- | --- |
| Unbounded connection/setup startup | 15-second end-to-end deadline, physical socket abort, bounded mod recovery |
| Setup construction failure leaves open transport | Abort setup-failed transport; release connection latches even if callbacks throw |
| Combined content/completion and multiple audio parts discarded | Process all parts before completion callbacks |
| First input stranded by readiness race | One synchronized provider-input queue for text/audio and setup drain |
| Prior summary lost | Include previous summary in immutable compaction input |
| Concurrent memory writes lost | Commit only captured entries; preserve appended entries and reject stale snapshots |
| Healthy session age consumes recovery budget | Reset the recovery episode after successful setup |
| Stale setup readiness | Volatile readiness, cleared on close/reconnect, duplicate acknowledgement protection |
| Binary buffer bounds ignored | Decode only the remaining slice, including direct/read-only buffers |

Additional fixes found during implementation:

- Transport reconnect invoked the mod's application shutdown override, permanently
  closing audio/session ownership. Reset now closes only the transport. A real local
  WebSocket regression first reproduced this, and in-world verification now checks
  audible output after reconnect.
- Idle post-turn text could wait forever for a nonexistent active output turn.
  It now queues immediately unless an actual turn is playing.
- Built-in tool world mutations ran on the WebSocket callback thread. They now use
  bounded server-thread dispatch, rechecking session/entity availability. A task that
  times out while queued is cancelled before it can mutate the world later.
- Live compaction closed its transport before the server-thread commit, allowing
  maintenance to discard its reservation. The owner now closes after applying the
  snapshot, and terminal callbacks are single-shot.
- The compaction feature guard was inverted; enabled memory summaries no longer
  disable compaction. Mutable fact/event list access was replaced with snapshots.

Verification includes deterministic race tests, actual loopback WebSocket handshakes,
silent HTTP and missing setup acknowledgement injection, setup exceptions, and real
Minecraft client/world runs on both loaders. The in-world fixture creates a MineColonies
citizen and exercises production prompt construction, urgent foreground ownership,
queued text/audio, combined output/audio drain, reconnect, cancellation, and addon
provider-status queries. It uses a local provider, not API credits. This does not yet
automate microphone hardware, every built-in action, or long-duration multiplayer load.

The local real-provider check on 2026-09-08 passed one session and one short turn in
2.315 seconds, with no skipped test and no retries. No credentials are checked in.
See [automated-verification.md](automated-verification.md) for commands and CI scope.
The fixed library is version 2.4.1; publish it before running mod CI without the local
composite checkout. No remote publish or workflow execution was performed here.

## The reported CONNECTING failure

The existing NeoForge log records urgent contact entering `NEW -> CONNECTING` at
23:04:46.459 and direct player conversation entering it at 23:05:25.452. Neither
logs `websocket opened` or `Gemini setup complete` before manual closure at
23:05:33.662 / 23:05:33.706. The urgent session therefore remained in that state
for about 47 seconds. These observations precede this review's tests.

A real Gemini integration test using the configured key subsequently passed
setup, audio reception, and `turnComplete` in 2.515 seconds. It used one session,
one short prompt, and no retries. This rules out a consistently invalid key or
unavailable model at test time; it does not identify which network stage stalled
in the earlier game process. No thread dump of that original stalled connection
was available.

## Findings

### 1. P1 — Connection startup has no effective end-to-end deadline

Library: `GeminiLiveClient.java:33`,
`websocket/client/WebSocketClient.java:141`, `:506`, `:689`.
Mod: `ConversationManager.java:154`, `:958`, `:967`,
`internal/session/ForegroundSessionRegistry.java:309`,
`manager/GeminiWsClient.java:393`.

`GeminiLiveClient` selects the transport constructor whose connect timeout is
zero. Its real `Socket.connect` call receives zero (unlimited). TLS/HTTP startup
has no separate deadline, and the connection-loss timer starts only after the
WebSocket opens. A responsive WebSocket that never acknowledges Gemini setup
also has no setup deadline.

The mod's fallback startup cleanup cannot rescue the direct-player path:
`reservation.activate()` marks the registry ACTIVE immediately after the
asynchronous `connect()`, while startup purging only checks RESERVED/STARTING.
The provider controller has no timer; its recovery window is consulted only
when something asks to recover. Queued input explicitly does nothing while
CONNECTING/SETTING_UP. A stalled attempt can retain a slot and buffered audio.

Evidence: `GeminiLiveClientTest.connectionAttemptHasAFiniteSocketTimeout`
executes the production connect path with an injected socket and fails because
the actual timeout is zero. The earlier game log demonstrates the matching
pre-open symptom, but does not distinguish TCP, TLS, and HTTP-handshake stalls.

Fix direction: impose a per-attempt deadline covering connect through
`setupComplete`, physically abort the old socket on expiry, and drive bounded
recovery/terminal cleanup. Keep transport readiness separate from gameplay
lifecycle activation; do not make ACTIVE registry entries invisible to startup
supervision merely because construction succeeded.

### 2. P1 — Setup exceptions leave an open transport that recovery refuses to replace

Mod: `manager/GeminiWsClient.java:409`, `:744`, `:1077`.
Library: `websocket/WebSocketImpl.java:757`.

A runtime exception from setup/prompt construction, other than the specifically
caught exhausted-voice exception, reaches `onError` after the transport has
already marked itself OPEN. `onError` calls `scheduleRecovery`, which immediately
returns true when `isOpen()` is true. No setup is sent, no reconnect is scheduled,
and no terminal cleanup occurs. The displayed status can remain CONNECTING even
though the internal state reached SETTING_UP. This is a separate failure path
from the captured pre-open hang.

Fix direction: distinguish a usable ACTIVE session from an open transport.
Setup failure must close/abort and recover or terminate; an open socket is not
proof of successful setup. Source-confirmed; no Minecraft-backed injected
setup-exception regression was added in this pass.

### 3. P1 — Valid combined server content loses completion events and output

Library: `GeminiLiveClient.java:148` through `:216`.
Mod consumers: `manager/GeminiWsClient.java:630`, `:700`,
`conversations/LiveConversationWsClient.java:171`.

The parser returns immediately on `generationComplete`, `interrupted`, and
`turnComplete`, and after the first audio part. Thus a message containing both
completion flags delivers only `onGenerationComplete`; final transcription or
model content in that message is also dropped. Multiple audio parts are reduced
to the first. The mod depends on `onTurnComplete` to mark the provider turn done,
commit/forward audible transcripts, flush pending text, and end conversations.
Losing that callback can leave the conversation waiting indefinitely after audio
finishes.

Evidence: failing tests `combinedCompletionFlagsDeliverBothCallbacks`,
`finalContentIsDeliveredBeforeCompletion`, and `allAudioPartsAreDelivered`.
These are synthetic schema-valid messages, not a claim that the short live test
observed every combination. Google's [Live API reference](https://ai.google.dev/api/live)
defines these fields together on `BidiGenerateContentServerContent`; the library's
local Gemini skill also explicitly requires processing every content part.

Fix direction: process every applicable field/part, deliver content before
completion, and preserve the intended interruption/turn-completion ordering.

### 4. P1 — Input can be stranded when setup completes between readiness check and enqueue

Mod: `manager/GeminiWsClient.java:772`, `:797`, `:801`, `:1086`, `:1123`.

Readiness is checked outside the pending-input locks. A sender can observe
not-ready and pause, while the WebSocket thread marks ACTIVE and drains an empty
queue. The sender then enqueues its first prompt, sees an open/ACTIVE connection
in `ensureConnectionForQueuedInput`, and returns. Nothing drains that queued
prompt until another setup. This affects urgent-contact text as well as initial
player audio and can produce a silent LISTENING session.

Fix direction: make the readiness-check/enqueue decision atomic with setup's
drain, or recheck readiness while holding the same lock. Preserve queued data
when a send fails during disconnect. Source-level concurrency finding; a
barrier-controlled test at the real mod input/setup seam is still needed.

### 5. P1 — Repeated memory compaction forgets the previous summary

Mod: `conversations/memory/MemoryCompactionService.java:240`, `:224`;
`conversations/memory/data/CitizenMemories.java:157`.

The compaction prompt contains only raw events and facts, omitting
`getSummarizedMemory()`. Applying the result overwrites the existing summary and
clears raw detail. After the second compaction, history represented only in the
first summary is irretrievably absent from memory. Both Flash and Live use this
prompt/application path.

Fix direction: include the existing summary as input to subsequent compactions.
Source-confirmed; no real-provider memory test was run.

### 6. P1 — Compaction clears memories written after its input snapshot

Mod: `conversations/memory/MemoryCompactionService.java:110`, `:168`, `:212`;
`conversations/memory/data/CitizenMemories.java:157`.

While the asynchronous provider request runs, tools/addons can append new facts
and events. Completion replaces the summary and clears the current entire
corpus, including entries the request never saw. A background reservation is
not a lock against those memory writes. This loses confirmed facts as well as
their provenance.

Fix direction: capture an immutable, versioned input snapshot; remove only the
entries covered by the successful result, retaining subsequent writes. Apply
the result and release compaction ownership together on the server thread.
Source-level interleaving finding; needs an integration test around snapshot,
concurrent write, and result application.

### 7. P2 — Healthy session age consumes the entire recovery window

Mod: `manager/GeminiWsClient.java:54`,
`internal/session/ProviderRecoveryController.java:168`, `:197`.

The five-minute recovery window starts at controller construction, not at the
first failure. Successful setup resets only consecutive attempts. A session
that has worked for over five minutes cannot recover even its first transient
disconnect: `beginRecovery` immediately produces RECOVERY_EXHAUSTED. The current
controller tests confirm the lifetime-window policy, so those tests passing
does not establish that long player conversations can recover.

Fix direction: bound each failure/recovery episode using elapsed failure time
and a finite attempt count; reset that episode after successful setup. Keep a
separate lifetime cap only if it is an intentional product requirement.

### 8. P2 — Library setup readiness survives disconnect and a new handshake

Library: `GeminiLiveClient.java:26`, `:45`, `:58`.

`setupComplete` becomes true once and is never cleared. Library consumers can
mistake a disconnected or freshly reopened connection for a configured session.
It is also not volatile despite its cross-thread getter. The mod mostly uses
its separate recovery controller for readiness, reducing its exposure, but
other library consumers and the library's own pre-setup message guard are
affected.

Evidence: two direct lifecycle tests and the real-localhost
`GeminiLiveWebSocketTest.remoteCloseInvalidatesSetupReadiness` fail. Fix by
resetting readiness at transport lifecycle boundaries and providing visibility
for readers.

### 9. P3 — Binary decoding assumes a whole array-backed buffer

Library: `GeminiLiveClient.java:68`.

`bytes.array()` ignores position, limit, and array offset and throws on direct
or read-only buffers. The bundled ordinary binary-frame path uses an
array-backed buffer, so this is not an explanation for the reported startup
hang. It is a reproducible public callback limitation for alternate transports
or callers using slices/direct buffers.

Evidence: `binaryMessagesRespectBufferBoundsAndSupportDirectBuffers` fails with
`UnsupportedOperationException`. Decode a duplicate/slice's remaining bytes.

## Automated checks added

In the sibling library:

- `GeminiLiveClientTest`: seven targeted negative regressions.
- `GeminiLiveWebSocketTest`: actual localhost setup, binary audio, tool-response
  ID, turn completion, and remote-close readiness tests.
- `GeminiLiveIntegrationTest`: optional real Gemini setup/audio/turn test, one
  session and one short response, no retries. Hard waits and forced socket
  cleanup also cover a production client whose connection is stuck.
- SLF4J test runtime dependency for both loaders, needed to instantiate the
  production WebSocket implementation outside Minecraft.

From Talking Colonists:

```bash
# Offline, both library loaders serially; deliberately fails on findings above.
bash scripts/test-gemini-websocket.sh offline

# Opt-in real Gemini call; defaults to the NeoForge YACL config's key/model.
bash scripts/test-gemini-websocket.sh live
```

Set `GEMINI_LIVE_LIBRARY_DIR` for another library checkout. Live credentials may
instead come from `GEMINI_API_KEY` or `GEMINI_LIVE_TEST_CONFIG`; a config path
takes precedence if both are supplied. `GEMINI_LIVE_TEST_MODEL` overrides the
model selection. Use a free-tier project key for these live checks. The runner
does not enable billing, change concurrency limits, or retry quota failures.
It forces test execution so Gradle cannot satisfy a live check with a cached
result. Offline mode removes credential environment variables and skips live
requests. The live test does not validate MineColonies prompt/tool generation,
microphone capture, in-game audio playback, or the mod's ownership transitions.

## Validation

| Check | Result |
| --- | --- |
| Talking Colonists NeoForge tests | 126 passed |
| Talking Colonists Forge tests | 126 passed |
| Library offline tests, each loader | 5 passed, 8 failed as reproduced above, 1 live test skipped |
| Real Gemini integration | Passed twice sequentially, including the shell runner; one session per invocation, no retries |
| Real client launch/world-entry smoke | NeoForge and Forge passed with normal assets |
| Shell syntax and tracked whitespace checks | Passed |

The offline localhost round trip passes on both loaders. The eight failures are
seven targeted lifecycle/parser tests plus the real-localhost close/readiness
test. Four existing library Flash/TTS tests still pass. Running the same focused
tests repeatedly reproduced the assertions; the seven targeted cases take less
than one second in the test JVM (Gradle startup adds overhead).

No runtime Java, addon API, resources, credentials, or active-version files were
edited. Changes are the tests, the two library test-runtime dependencies, the
release-test runner, this report, and the refreshed client-smoke marker (staged
as required by AGENTS.md). No runtime fixes or commits were made. The new
failing tests will make an unfiltered library test/build fail until the bugs are
fixed; this is intentional evidence, not a green release gate.

`bash scripts/test-client-smoke.sh` completed successfully, restored the normal
Stonecutter source view, and wrote the genuine `.client-smoke-verified` marker.
Launch logs are `/tmp/client-smoke-1.21.1-neoforge-twfM4X.log` and
`/tmp/client-smoke-1.20.1-forge-anPqBe.log`. Generated unit-test logger files were
moved out of the worktree to `/tmp/talking-colonists-review-logs.nGW5As`.
