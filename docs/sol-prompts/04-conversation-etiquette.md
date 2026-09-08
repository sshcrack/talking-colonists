# Conversation etiquette and quiet mode

Work in `/home/hendrik/Documents/java/minecraft/talking-colonists`. Read `AGENTS.md` and inspect the current implementation before making changes. Preserve unrelated working-tree changes. Implement this feature through completion, with focused regression tests and both supported builds. Run every Minecraft client in a virtual display: `DISPLAY= CLIENT_SMOKE_OFFLINE=1 bash scripts/test-client-smoke.sh` (stage intended runtime changes first, then stage the generated verification marker). Keep the existing mouth rendering, conversation gestures, and partner-aware presentation intact. Follow the existing configuration/translation conventions. Do not publish or send Discord messages. Report behavior changes, validation, and remaining limitations.

Improve who is allowed to initiate speech near a player. Inspect `ConversationManager`, `internal/session`, foreground ownership, ambient admission, and cooldown registries.

Prevent several citizens from addressing one player at once. Prefer foreground player conversations over ambient chatter, allow genuinely urgent existing events to be queued sensibly, and add a player-controlled quiet mode that suppresses unsolicited speech without breaking manually started conversations. Keep admission deterministic and server-authoritative. Reuse existing lifecycle ownership/cancellation, and bound every queue with expiry. Avoid global silence that penalizes unrelated players.

Acceptance: concurrent citizens addressing one player, two independent players, foreground takeover, queued citizen death/unload, reconnect, and quiet mode all have focused tests. Check stale reservations and audio cleanup. Preserve addon admission-rule semantics and document any intentional API change.
