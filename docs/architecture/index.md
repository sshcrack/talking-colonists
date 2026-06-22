# Architecture

## Data Flow

```mermaid
flowchart LR
 Player["Player (Microphone)"] --> VC["Simple Voice Chat Plugin"]
 VC --> CM["ConversationManager"]
 CM --> GWC["GeminiWsClient / CitizenWsClient"]
 GWC --> Gemini["Gemini Live API"]
 Gemini --> GWC
 GWC --> CM
 CM --> VC
 VC --> PlayerSpeakers["Player (Speakers)"]

 CM --> CIT["Citizen Entity"]
 CIT --> MC["MineColonies API"]
 MC --> CIT

 subgraph AI["AI Tools"]
 AIT["AITools.register()"]
 AIT --> GF["General Tools"]
 AIT --> PF["Player-Only Tools"]
 end

 GWC --> AIT
 AIT --> MC
```

## Major Subsystems

| Subsystem | Package | Purpose |
|-----------|---------|---------|
| **Entrypoints** | `me.sshcrack.mc_talking` | `McTalking` (common init), `McTalkingClient` (client), `McTalkingVoicechatPlugin` (voice chat) |
| **Conversation Manager** | `.conversations` | Central orchestrator for all conversations, slot management, cooldowns |
| **Config** | `.config` | YACL-based configuration with API/General/Citizens categories |
| **Gemini Client** | `.manager` | `GeminiWsClient`, `CitizenWsClient` — WebSocket clients for the Gemini Live API |
| **Prompt System** | `.api.prompt`, `.manager` | Prompt view/provider SPI for constructing AI prompts |
| **AI Tools** | `.manager.tools` | 15+ function-calling tools the AI can invoke during conversations |
| **Memory** | `.conversations.memory` | Conversation memory, compaction, relationship tracking |
| **Services** | `.handler`, `.rumor`, `.broadcast`, `.pregen` | Rumor mill, broadcast, pregeneration, urgent contact |

## Multi-Loader Architecture

```mermaid
flowchart TD
 subgraph Shared["Shared Code (src/main/java)"]
 Platform["Platform Interface"]
 Mixins["23 Mixin Classes"]
 Items["3 Items"]
 Config["McTalkingConfig (YACL)"]
 end

 subgraph NeoForge["1.21.1 NeoForge"]
 NFImpl["NeoforgePlatformImpl"]
 NFBuild["build.neoforge.gradle.kts"]
 NFAW["accesswidener"]
 end

 subgraph Forge["1.20.1 Forge"]
 FImpl["ForgePlatformImpl"]
 FBuild["build.forge.gradle.kts"]
 FCFG["access transformer .cfg"]
 end

 Platform --> NFImpl
 Platform --> FImpl
```

## Mod Initialization

1. **`McTalking` constructor** — Called by mod loader
2. `AITools.register()` — Registers all AI function-calling tools
3. `McTalkingConfig.loadConfig()` — Loads YACL config
4. `ModItems.register()` — Registers 3 items via DeferredRegister
5. Event bus registration — Server event handlers, network payloads
6. `onCommonSetup` — `ColonyEventSubscriber.register()` for MineColonies hooks

## Sub-Pages

- [**Prompt System**](prompt-system.md) — How AI prompts are constructed
- [**AI Tools**](ai-tools.md) — Function-calling tools reference
- [**Voice Chat Integration**](voice-chat.md) — Simple Voice Chat integration
- [**Platform Abstraction**](platform-abstraction.md) — Multi-loader architecture
