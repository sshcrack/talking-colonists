# Personality expressed through behavior

Work in `/home/hendrik/Documents/java/minecraft/talking-colonists`. Read `AGENTS.md` and inspect the current implementation before making changes. Preserve unrelated working-tree changes. Implement this feature through completion, with focused regression tests and both supported builds. Run every Minecraft client in a virtual display: `DISPLAY= CLIENT_SMOKE_OFFLINE=1 bash scripts/test-client-smoke.sh` (stage intended runtime changes first, then stage the generated verification marker). Keep the existing mouth rendering, conversation gestures, and partner-aware presentation intact. Follow the existing configuration/translation conventions. Do not publish or send Discord messages. Report behavior changes, validation, and remaining limitations.

Make existing personality settings affect conversational choices and timing, with a small, explicit policy. Inspect existing personality configuration, prompt providers, and conversation admission/cooldowns first.

Implement restrained differences such as willingness to initiate small talk, brevity preference, and frequency of follow-up questions. Derive these from existing personality data using bounded values; keep safety, sleep, work, combat, and admission constraints authoritative. Use deterministic test inputs. Let personality influence choices without rewriting a citizen's job AI or inventing persistent traits on every request.

Acceptance: contrasting configured personalities produce measurably different eligible choices in fixtures, while both respect quiet mode, cooldowns, player ownership, and work interruptions. Check defaults preserve familiar behavior. Document which differences are enforced by code and which remain provider prompt preferences.
