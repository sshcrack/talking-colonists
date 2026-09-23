# Track X — Addon ideas

Specifications for addons built **outside core** on the public API. Each lists the Track A tasks
it needs; an addon can start once those land in a dev build. Existing addons are listed for
context so ideas do not overlap:

- **Colonist Errands** (Lovkar-Squid) — errands, logistics, jobs, military, promises, rapport,
  guard leaderboard, watchdogs.
- **Colony Meetings** (Joveira, 1.20.1 Forge, in development) — attendance, podiums, seating, hand
  raising, floor control. Uses `ControlledConversationSession`; see
  `docs/meetings-integration.md`. Check that the developer API artifact is published for the
  1.20.1 Forge variant.

| Addon | Pitch | Needs | Size |
| --- | --- | --- | --- |
| X0 Addon template | Starter repo showing registration, feature detection, a tool, a contributor | A0 | S |
| X1 Notice Board & Loudspeaker | Write or speak colony news; citizens discuss it and pin replies | A1, A3 (A10) | M |
| X2 Colony Gazette | Daily newspaper book written from colony events | A2, A3, A7 | M |
| X3 Town Hall & Elections | Mayor elections, petitions, polls, campaign speeches | A1, A2, A3, A5, Colony Meetings | L |
| X4 Postal Service | Letters to citizens, replies arrive as books | A3 | M |
| X5 Tavern Recruiter | Talk visitors into joining or haggle their cost | A6, A8 | M |
| X6 Campfire Nights | Evening storytelling from citizens' memories | A7 | S |
| X7 Citizen Quests | Citizens ask the player for favours, with rewards | A2, A3, A5, A6 | L |
| X8 Diplomacy | Ambassadors negotiate between colonies | A3, A9 | L |
| X9 School Lessons | Children remember what the teacher taught them | A5 | M |
| X10 Court & Justice | Citizens bring disputes to a player judge | A3, A5, A6 | M |
| X11 Tour Guide | A citizen shows new players around the colony | A6 | S |

---

## X0 — Addon template

A minimal public repository (both loaders via Stonecutter, or NeoForge only) that registers an
addon, checks `TalkingColonistsApi.supports(...)`, adds one prompt contributor and one query tool,
and listens to lifecycle events. Linked from `docs/addon-api.md`. Lowers the barrier for everything
below.

## X1 — Notice Board & Loudspeaker

Requested on Discord ("Newspaper" thread) for multiplayer colonies with politics.

- **Notice board block**: players place a written book or sign text on it → `publishBroadcast`
  with the board as source and `PROPAGATE_FROM` the board position (A1).
- **Replies**: citizens who read a notice can pin a short reply or petition, generated with A3 and
  shown on the board GUI, so players get feedback from the colony.
- **Loudspeaker item**: text entry first; voice input through A10 once available. Publishes with
  `COLONY_IMMEDIATE` scope and optional voiced announcements.

## X2 — Colony Gazette

Every in-game morning the librarian or teacher writes a `written_book` summarising yesterday:
raids, births, deaths, new buildings, rumors, broadcasts (A2 for events, A3 for text). Schedule
generation when A7 reports spare capacity. Text-only, so it costs almost no quota. Optional
printing press block that makes copies.

## X3 — Town Hall & Elections

Mayor elections and petitions for multiplayer colonies. Candidates (players) give speeches in a
Colony Meetings session; each citizen votes using A3 structured output grounded in its memory of
each candidate (`CitizenMemoryService.snapshot`) and promises heard (A5). Results are published
as a broadcast (A1) and recorded as a colony event (A2). The mayor gets configurable perks
(for example tool permissions via addon tools).

## X4 — Postal Service

Write a letter (book and quill) addressed to a citizen and give it to a courier. The citizen
replies after a delay with an in-character letter generated through A3, delivered to the player's
mailbox. Confirmed outcomes go into memory. Works without a microphone and suits servers where
players are online at different times.

## X5 — Tavern Recruiter

Talk to MineColonies visitors in the tavern (A8) in a scoped conversation (A6) with a
`negotiate_recruit_cost` tool that lowers the cost based on how the conversation went, within
limits. A recruited visitor keeps what was said.

## X6 — Campfire Nights

At dusk, reserve 3–5 idle citizens (`reserveActivity`), gather them at a campfire block, and run
an autonomous group discussion where each tells a story from its own memories. Only start when A7
reports enough capacity; players nearby can listen or join with a player statement.

## X7 — Citizen Quests

Citizens ask the player for favours based on real needs and colony events (A2): "find my lost
pickaxe", "bring flowers for Anna's funeral". Quest text via A3, acceptance heard through A5 and
confirmed with a tool in a scoped conversation (A6). Rewards: happiness, items, relationship
changes through confirmed outcomes. Complements Colonist Errands' promises, which go the other way.

## X8 — Diplomacy

Ambassadors from allied or rival colonies meet in a cross-colony controlled session (A9) and
negotiate trade or alliances; outcomes are proposed through A3 structured output and applied by
the addon. Builds on the colony-connection context core already has.

## X9 — School Lessons

Record what the teacher says during lessons (A5) and store short facts in each attending child's
memory; children bring them up later and still remember as adults.

## X10 — Court & Justice

Citizens bring disputes (theft, broken promises, quarrels from relationship memory) to a player
judge in a scoped conversation (A6). Testimony is recorded through A5; the verdict is applied by
addon tools and remembered by everyone involved (A3 for summaries).

## X11 — Tour Guide

A citizen walks new players around the colony, explaining buildings from verified facts, in a
scoped conversation (A6) with a guide agenda. Useful for multiplayer onboarding.
