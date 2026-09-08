# Speech that helps the player act

Work in `/home/hendrik/Documents/java/minecraft/talking-colonists`. Read `AGENTS.md` and inspect the current implementation before making changes. Preserve unrelated working-tree changes. Implement this feature through completion, with focused regression tests and both supported builds. Run every Minecraft client in a virtual display: `DISPLAY= CLIENT_SMOKE_OFFLINE=1 bash scripts/test-client-smoke.sh` (stage intended runtime changes first, then stage the generated verification marker). Keep the existing mouth rendering, conversation gestures, and partner-aware presentation intact. Follow the existing configuration/translation conventions. Do not publish or send Discord messages. Report behavior changes, validation, and remaining limitations.

Improve contextual citizen speech so it references current, useful colony facts. Inspect prompt providers, tools, colony requests, and existing mumbling cooldowns. Prioritize one concrete situation: a worker blocked by a missing material can say exactly what is missing and where it is needed.

Use authoritative request data and aggregate quantities without fabricating locations or requirements. Revalidate before delivering delayed speech. Deduplicate reminders, respect player distance and work state, and avoid repeating a request that has already been fulfilled or canceled. Extend existing prompt/context APIs rather than creating a second colony snapshot pipeline.

Acceptance: tests cover fulfilled/canceled requests, changed quantities, unavailable location data, delayed responses, and repeated reminders. Provide a local fixture demonstrating useful speech with no paid provider. Preserve normal worker scheduling and existing reminder settings.
