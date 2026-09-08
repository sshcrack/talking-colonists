# Conversation visuals

Talking Colonists keeps MineColonies' normal names, profession models, outfits, hats,
armor, and equipment. Conversation state no longer extends the name label.

- Speaking citizens use a small animated speech bubble and subtle head/empty-hand
  gestures. Bubble and hand gestures follow the existing `TALKING` state. Mouth opening
  follows received audio energy; it is not phoneme lip sync.
- Listening uses a microphone icon and gentle nodding. Thinking uses moving dots
  and a slight change in head pose. Connecting uses a rotating ring; a voice limit
  uses a pause symbol; errors and urgent contact use an exclamation mark.
- Looking directly at an active citizen shows a short explanation above the hotbar.
  This is state feedback, not subtitles. The status packet also identifies the
  foreground conversation partner, so listening feedback can distinguish the
  participating player from nearby observers.
- World bubbles appear only for living, awake, visible citizens within 16 blocks
  and line of sight. Hiding the HUD hides the bubbles and hint.
- Sleeping, working, using items, and swinging retain their normal animation.
  Hand gestures apply only to empty-hand poses while stationary and not riding.

In **General → Interaction**, **Speech bubbles** and **Conversation hint** can be
turned off separately. **Reduce conversation motion** keeps the icons static and
turns off the added gestures and mouth movement.

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

Citizen mouths now respond to the energy of received Simple Voice Chat entity audio. The mouth follows the rendered head, uses world lighting, and closes during silence or after lost audio. This is audio-reactive movement, not phoneme recognition. Audio samples are measured and discarded; no voice recording is stored.

The initial mouth layer supports built-in adult citizen faces. Children, custom textures, invisible/sleeping citizens, and equipped or displayed helmets retain their original faces. A client setting disables mouths independently; reduced conversation motion disables mouths and added gestures together. Existing MineColonies models and skins remain in use, with no GeckoLib dependency.

Conversation participants are synchronized separately from AI status. A nearby observer sees generic listening feedback; the participating player sees “Your turn · Listening to you.” Looking at an idle citizen while holding the communication device reveals the start control. Click the same citizen again to end; right-clicking air with the device also ends the player's active conversation. Speaking participants receive an interruption hint. Idle citizens gently orient their heads toward their conversation partner while preserving work, combat, sleep, and held-item poses.

The mouth plane was prototyped in Blockbench Web at head-space Z −4.52, with a two-pixel maximum width. Rendering scales its opening from playback energy, with a short stale-frame release.

## Verification

The existing two-version client smoke test now also checks the real citizen model's
speaking pose, reset after speaking, reduced-motion behavior, and synced visual
states. It saves in-game screenshots of speaking, listening, thinking, and errors to
`/tmp/colonist-redesign/screenshots/`. The fixture runs only in disposable auto-quit
worlds, with its existing local mock voice provider.

If Maven is unavailable and dependencies are already cached, run the smoke test with
`DISPLAY= CLIENT_SMOKE_OFFLINE=1 bash scripts/test-client-smoke.sh`. This changes dependency
resolution only; the real in-world checks and verification fingerprint still run.

Both supported loaders are built and launched serially under Xvfb. The in-world fixture checks foreground ownership synchronization and cleanup, model pose reset, reduced motion, status delivery, and mouth closure after playback stops. Mouth energy tests cover silence, end-of-transmission, bounds, entity isolation, expiry, and sample-array independence.

Screenshots use a disposable test world and synthetic audio/state fixtures. They demonstrate actual Minecraft rendering; they are not an AI-generated mockup. Automated checks do not establish compatibility with every third-party resource pack or shader.
