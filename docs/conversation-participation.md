# Conversation participation and citizen status

Talking Colonists treats three foreground-conversation facts independently:

- **Participation** — whether the current foreground ownership token is associated with a direct player.
- **Provider readiness** — whether Gemini is not ready, connecting, ready for input, or recovering.
- **Audible activity** — whether the citizen is idle, thinking, or audibly talking.

The visible citizen status is derived from those facts. There is deliberately no combined state enum for every possible permutation. Foreground ownership remains authoritative in `ForegroundSessionRegistry`; provider recovery remains authoritative in `ProviderRecoveryController`.

## Player-facing status contract

`LISTENING` is an actionable promise. It is shown only when the current foreground owner is a direct-player conversation **and** that owner's provider is ready to accept microphone input. The English name-tag label remains the concise `Listening`; the stricter ownership/readiness rules make that promise accurate.

This produces the following behavior:

- A direct conversation shows connecting feedback first and becomes `LISTENING` only after provider setup succeeds.
- During recovery, the listening promise is removed and reconnecting feedback is shown until setup succeeds again.
- Audible citizen output can show `TALKING`; intermediate model work can show `THINKING`. These facts are independent of whether a player is associated.
- Urgent contact remains a one-sided ambient announcement. It may show connecting, thinking, and talking feedback, but provider readiness by itself never shows `LISTENING`. The player enters a direct conversation with the existing talking-device interaction.
- When an already-ready ambient `CitizenWsClient` is promoted by a player takeover, the same ownership token is associated with that player. Microphone routing and presentation immediately use that authoritative association; if the citizen is idle the status becomes `LISTENING`, while audible output can continue to show `TALKING` and still permit barge-in.
- Completion and player disconnect remove the listening promise. A delayed callback from an old token cannot clear or overwrite a replacement owner's status.

## Thread and ownership rules

Provider callbacks may arrive off the Minecraft server thread. `MinecraftConversationParticipationAdapter` records provider/audible facts in the Minecraft-free participation module, then queues only the presentation application. The foreground token is revalidated **when the queued action executes on the server thread**. Terminal cleanup clears presentation only when no newer foreground owner exists.

Microphone packets use the same participation module. A packet is forwarded to Gemini only when the current foreground token belongs to that player and the module records the provider as ready. This keeps the displayed participation promise and actual voice routing aligned. Player barge-in remains possible while the citizen is talking because provider readiness and audible playback are separate facts.

The direct microphone path is additionally owned by `MicrophoneTurnModule`. Each provider ownership token gets its own local microphone-turn identity, monotonic timing, scheduled work, and speech progress. Simple Voice Chat Opus is decoded before local speech classification; packet length is not used as speech evidence. Real quiet microphone PCM is still forwarded so Gemini's automatic VAD remains authoritative, but only sustained decoded speech may cancel local citizen playback. Generated trailing padding is tagged separately and can never invoke local barge-in.

After qualifying speech becomes quiet, the module owns both repeating padding and its stop/response timeout. Every callback captures the exact provider-session and microphone-turn identity, and replacement/disconnect/shutdown cancels all owned work. A cancelled callback is still harmless if it races execution because it revalidates ownership before sending padding or changing response state. When a closed microphone turn has no provider progress for the bounded response window, the temporary `THINKING` presentation is removed and the citizen returns to the truthful ready/listening presentation instead of appearing stuck indefinitely.

## Addons

No public addon API signature changed. `CitizenConversationService.providerStatus(citizen)` continues to report provider transport/recovery state; it is not itself proof that a player is participating. Addons that render their own conversation UI should combine provider readiness with the authoritative direct-player association exposed by the conversation API rather than interpreting provider readiness as `LISTENING`. Ambient/controlled provider readiness must not be presented as permission for player microphone input.
