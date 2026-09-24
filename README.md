You can interactively talk to citizens in your minecolonies just as if they were alive!

## Setup Guide
0. Install all dependencies ([Minecolonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies), [Voicechat](https://modrinth.com/mod/simple-voice-chat) and [Gemini Live Lib](https://www.curseforge.com/minecraft/mc-mods/gemini-live-lib))
1. Head to the [Google AI Studio](https://aistudio.google.com/u/0/apikey) and create an API key by pressing the blue button:

![blue Create API button](https://github.com/talking-colonists/talking-colonists/blob/neoforge-1.21.1/imgs/create_btn.png?raw=true)
3. Copy the token

4. Either the config file via text editor in `config/mc_talking-common.toml` or via the in-game GUI. Set the token and your desired [speaking language](https://ai.google.dev/gemini-api/docs/live#supported-languages):
 ```toml
 #This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey
gemini_key = "Put your API key here"
 ```
McTalking is designed to work with a Gemini API key from Google AI Studio without requiring billing. The default models have Gemini Developer API free-tier access, and the default foreground/background concurrency is intentionally conservative. Google can change model-specific rate limits over time, so check your project's current limits in AI Studio if you encounter `429`/quota messages; paying is optional and only increases available capacity.

## Usage
Craft a Citizen Communication Device using a Book and Quill and a Redstone Torch (I would really appreciate it if someone could create a better texture for it):
![Crafting Recipe](https://github.com/talking-colonists/talking-colonists/raw/neoforge-1.21.1/imgs/crafting_recipe.png?raw=true)
Then left-click on a citizen you want to talk to and something similar to this will show:
![Citizen that can be talked to](https://github.com/talking-colonists/talking-colonists/raw/neoforge-1.21.1/imgs/ingame.png?raw=true)

Now the citizen will complain if they are unhappy or missing some resources, just talk right away!

No microphone, or a noisy room? While you talk to a citizen, start a chat line with `@` (for example `@ Could you bake some bread?`) and it goes to that citizen instead of server chat. Run `/citizen_chat on` to send every chat line to the citizen you are talking to, and `/citizen_chat off` to stop. The prefix can be changed in the config.

## Can I include this in my modpack?
Yup

## Addon API
Talking Colonists 2.0.0 introduces addon API generation 2 as a breaking, first-class integration baseline. Addons can register AI
tools and prompt context/providers, consume normalized citizen snapshots, inspect typed conversation
eligibility/start results, observe conversation lifecycle, work with citizen memories, modify
pregenerated prompt constraints, hold renewable activity leases, and run ordinary or controlled
meeting/council conversations through `me.sshcrack.mc_talking.api`.
The supported sources are compiled separately from implementation code and published as the
developer-only `me.sshcrack:mc_talking-api` artifact on the [public sshcrack Maven](https://maven.sshcrack.me/#/).
Addon projects can resolve it from `https://maven.sshcrack.me/releases` as a compile/IDE dependency
while requiring the normal Talking Colonists mod at runtime. **Players install only the normal
Talking Colonists mod; the API artifact is not a second mod.** See [docs/addon-api.md](docs/addon-api.md)
for coordinates, setup, and the supported API contract. Addon developers upgrading existing
integrations should use the general [addon migration guide](docs/addon-migration.md).

## Translating Talking Colonists

Translations are stored in `src/main/resources/assets/mc_talking/lang/` as JSON files. To contribute a translation:
1. Copy `en_us.json` to your language code (e.g., `pt_br.json` for Portuguese).
2. Keep the JSON keys unchanged; translate only the values.
3. Submit a pull request with your translated file.
