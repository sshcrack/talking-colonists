# Talking Colonists 2.1 — release notes (draft)

Changes since 2.0.0-beta.1. The GitHub release body is generated from commits by git-cliff; this
page is the curated summary to paste or link. Addon API: generation 2, `API_MINOR_VERSION` 1, fully
backward compatible with 2.0 addons.

## For players and server owners

- **Type to citizens:** while talking to a citizen, start a chat line with `@` to send it to the
  citizen instead of server chat. `/citizen_chat on` sends every line. The prefix can be changed in
  the config. (Q10)
- **Talk without the device:** sneak and left-click a citizen with an empty hand to start or end a
  conversation. This can be turned off in the config. (Q9)
- **Config presets:** Free Tier (default), Paid Key and Quiet Colony set the session and chatter
  options at once. Changing one of those options afterwards shows Custom. See
  `docs/config-presets.md`. (Q8)
- **Personality picker:** choose which of the 15 archetypes new citizens can get. (Q7)
- **Complaints that grow:** a lasting problem (no home, no job, sickness, idling) is mentioned in
  passing first, then complained about, then demanded. Young colonies get a grace period for
  housing. (Q5)
- **Less talking over each other:** a per-player ambient speech budget limits how many greetings,
  mumbles and rumors a player hears per minute. (Q4)
- **Clearer quota handling:** players and `/talking_colonists` show when Gemini's quota is used
  up (Q2). Operators are told when no API key is configured, with a working "Open config" link on
  both loaders (Q1).
- **Fixes:**
  - Citizen-to-citizen speech stays in the configured language.
  - Building style is no longer treated as a housing-quality complaint (Q6).
  - Translatable strings throughout.

**Config:** new entries are `configPreset`, `enableChatToCitizen`, `chatToCitizenPrefix`, the
`personality*` toggles, and the complaint settings. Existing values are kept. A config whose
session/chatter values differ from the defaults shows the Custom preset.

## For addon developers (API 2.1)

Every feature below is implemented. Check `TalkingColonistsApi.supports(ApiFeature.X)` before
using it, so the addon still runs on 2.0.x. Details are in `docs/addon-api.md` ("API 2.1 at a
glance"). Replacements for 2.0-era internals are in `docs/addon-migration.md` ("Moving to API
2.1").

| `ApiFeature` | What it adds |
|---|---|
| `BROADCAST_PUBLISHING` | Publish and retract colony broadcasts (notice boards, newspapers) |
| `COLONY_EVENTS` | Read and record colony events, and listen to new ones |
| `TEXT_GENERATION` | In-character text from a citizen or the colony's voice, plain or JSON |
| `PLAYER_TEXT_INPUT` | Send typed lines and game-event notes into a player's live conversation |
| `UTTERANCE_EVENTS` | One event per finished player, citizen or scripted line |
| `PLAYER_CONVERSATION_OPTIONS` | Scoped conversations: agenda, tool allow-list, purpose, memory extraction |
| `PROVIDER_BUDGET` | Session capacity, per-model quota state, quota listeners, config view and reload |
| `VISITOR_SPEAKERS` | Opt-in policy that lets tavern visitors talk, with a visitor prompt view |
| `CROSS_COLONY_SESSIONS` | Controlled meetings with citizens from several colonies (same dimension) |
| `PLAYER_SPEECH_CAPTURE` | Turn what a player says through Simple Voice Chat into text, without a citizen |
| `BROADCAST_REACH` | How many citizens have heard a published broadcast, out of all of them |

Also new: `ApiFeature` / `TalkingColonistsApi.supports(...)` / `requireSupported(...)` (A0), and
`ConversationLifecycleEvent.purpose()`.

**Not yet verified:** the in-world manual check for `PLAYER_SPEECH_CAPTURE` (listening indicator,
nothing captured after the end) is tracked in #149.

**Developer artifact:** `me.sshcrack:mc_talking-api:2.1.0-…` is published with the release. After
it is published, bump `api_baseline_version` in `gradle.properties` to it.
