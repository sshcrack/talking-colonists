# Verified facts and memory provenance audit

Audit date: 2026-09-07. Core baseline: `306fff74`. Colonist Errands comparison:
`Lovkar-Squid/colonist-errands@f270362aca847726087213c623508ad0a65354c1`.

The prompt snapshot must distinguish a current value of zero/empty from data that could not be
observed. `CURRENT` means the value was read while this prompt/tool snapshot was assembled;
`UNLOADED` means the authoritative entity needed for the fact was not loaded; `UNAVAILABLE`
means the backing API did not expose usable data. Memories are recollections, not observations.

| Area | Baseline source | Misleading case reproduced | Errands workaround | Required core fix |
| --- | --- | --- | --- | --- |
| Health | `CitizenPromptViewFactory.extractHealthPercent` | An unloaded citizen yields `null`, but callers cannot tell unloaded from an API failure. | ad-hoc health/report checks | Expose observation state alongside the value. `100` remains a real current value. |
| Equipment | no citizen-equipment fact | A fully equipped guard can repeat stale “missing gear” memories because core never states current kit. | `GuardGearCheck.promptLine`, using `CitizenData.getInventory()` rather than vanilla entity armor slots | Snapshot the citizen inventory once; expose current/empty/unavailable distinctly and tell the model current kit overrides recollection. |
| Housing | `CitizenWellbeingView.homeless`; factory uses `getHomeBuilding() == null && !guard` | A low-level but assigned residence can be described as poor housing while old recollection says “no home”; the boolean model cannot express unknown. | `HomeCheck.promptLine` scans colonies by citizen name and emits a “HOME TRUTH” block. | Explicit `HOUSED`, `GUARD_QUARTERS`, `HOMELESS`, `UNKNOWN` housing state with stable citizen-local lookup only. |
| Builder state | generic activity/work-state only | “Not currently laying blocks” conflates sleeping with waiting for materials and an unreadable/unloaded AI. | `BuildWatch.promptLine` maintains a separate watcher and name lookup. | Combine persisted asleep/job status, loaded worker state, and request snapshot into a bounded builder status. Sleeping wins over material-waiting. |
| Requests/stock | `extractCategorizedItemRequests` | No work building, unavailable request data, and genuinely zero open requests all collapse to empty lists. It also calls `warehouseHasStock`, which scans every colony building per prompt. | `SupplyCheck` caches scans; `CheckStockAction` performs explicit on-demand stock counting. | Prompt snapshot uses request lifecycle state only and records observation state; expensive stock counting remains action/tool-time work. |
| Current-vs-memory | `DefaultCitizenPromptProvider.addObservations` then `addMemory` | A current “fully supplied/housed/equipped” observation can be followed by contradictory stale prose without a precedence rule. | multiple `*Check.promptLine` blocks explicitly forbid stale claims | Emit one verified-current block with an explicit precedence/freshness rule before recollections. |
| Player promises | `PlayerConversationMemoryGenerator` | Input explicitly contains only citizen speech, yet the extraction prompt permits facts about the player. A citizen saying “you promised me bread” can therefore become a persisted player fact. | `MakePromiseAction` records a promise when the model calls a player action, keyed primarily by names | Citizen-only transcript may produce only citizen-statement provenance; it must never create a player statement/promise. Addons confirm gameplay outcomes through an idempotent, source-labelled API. |
| Persistence | `CitizenMemories` string lists + aggregate relationships | Facts/events have no speaker/source; relationship deltas cannot be tied to the event that caused them. | separate addon stores | Persist entry provenance + stable participant UUIDs and relationship contribution provenance; migrate legacy string/aggregate data as unattributed without loss. |

## Snapshot freshness contract

`CitizenPromptView` is a point-in-time snapshot assembled on the server thread. It does not perform
background refreshes and it does not promise that a value remains true for the duration of a live
conversation. The built-in `get_current_situation` tool creates a new snapshot when the model or an
action needs refreshed facts. Prompt construction avoids full-colony inventory scans; exact colony
stock questions belong in an explicit query/action where the cost is intentional.

Prompting reduces contradictions but cannot guarantee a model will never hallucinate. Consumers
should use current observations and tool results for gameplay decisions rather than recollection text.
