# Conversation visuals

Talking Colonists keeps MineColonies' normal names, profession models, outfits, hats,
armor, and equipment. Conversation state no longer extends the name label.

- Speaking citizens use a small animated speech bubble and subtle head/empty-hand
  gestures. The animation follows the existing `TALKING` state; it is not phoneme
  lip sync or an audio level meter.
- Listening uses a microphone icon and gentle nodding. Thinking uses moving dots
  and a slight change in head pose. Connecting uses a rotating ring; a voice limit
  uses a pause symbol; errors and urgent contact use an exclamation mark.
- Looking directly at an active citizen shows a short explanation above the hotbar.
  This is state feedback, not subtitles. It does not claim that the viewing player
  owns the conversation, because the existing status packet does not identify the
  session owner.
- World bubbles appear only for living, awake, visible citizens within 16 blocks
  and line of sight. Hiding the HUD hides the bubbles and hint.
- Sleeping, working, using items, and swinging retain their normal animation.
  Hand gestures apply only to empty-hand poses while stationary and not riding.

In **General → Interaction**, **Speech bubbles** and **Conversation hint** can be
turned off separately. **Reduce conversation motion** keeps the icons static and
turns off the added gestures.

The pixel speech-panel silhouette was prototyped in Blockbench Web. The runtime
uses colored geometry, preserving crisp pixels without relying on font symbols or
adding a texture-pack dependency. Its palette is slate ink `#25313C`, pale paper
`#F1F4EB`, speech teal `#83D5C7`, listening blue `#A6C9ED`, waiting gold `#E6C779`,
and error salmon `#F0A08D`. Shapes convey state independently of color.

## Animation approach

The client-only `CitizenConversationAnimationMixin` adds small pose offsets after
MineColonies' base citizen animation. Each normal model setup resets these offsets.
This preserves the existing profession-specific renderer and its equipment layers.

GeckoLib was considered. Its [entity renderer integration](https://github.com/bernie-g/geckolib/wiki/Geckolib-Entities-%28Geckolib4%29)
would introduce a replacement renderer/model pipeline. That is a larger migration
for MineColonies' profession-specific models and layers, and is not required for
these gestures. Full facial rigs and authored lip sync remain a separate design
choice; this change does not add GeckoLib as a dependency.

## Verification

The existing two-version client smoke test now also checks the real citizen model's
speaking pose, reset after speaking, reduced-motion behavior, and synced visual
states. It saves in-game screenshots of speaking, listening, thinking, and errors to
`/tmp/colonist-redesign/screenshots/`. The fixture runs only in disposable auto-quit
worlds, with its existing local mock voice provider.

If Maven is unavailable and dependencies are already cached, run the smoke test with
`CLIENT_SMOKE_OFFLINE=1 bash scripts/test-client-smoke.sh`. This changes dependency
resolution only; the real in-world checks and verification fingerprint still run.
