You can interactively talk to citizens in your minecolonies just as if they were alive!

## Setup Guide
0. Install all dependencies ([Minecolonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) and [Voicechat](https://modrinth.com/mod/simple-voice-chat))
1. Head to the [Google AI Studio](https://aistudio.google.com/u/0/apikey) and create an API key by pressing the blue button:

![blue Create API button](https://github.com/sshcrack/talking-colonists/blob/neoforge-1.21.1/imgs/create_btn.png?raw=true)
3. Copy the token

4. Either the config file via text editor in `config/mc_talking-common.toml` or via the in-game GUI. Set the token and your desired [speaking language](https://ai.google.dev/gemini-api/docs/live#supported-languages):
 ```toml
 #This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey
gemini_key = "Put your API key here"
 ```
(The free tier allows up to 3 citizens to be spoken at the same time)

## Usage
Craft a Citizen Communication Device using a Book and Quill and a Redstone Torch (I would really appreciate it if someone could create a better texture for it):
![Crafting Recipe](https://github.com/sshcrack/talking-colonists/raw/neoforge-1.21.1/imgs/crafting_recipe.png?raw=true)
Then left click on a citizen you want to talk to and something similar to this will show:
![Citizen that can be talked to](https://github.com/sshcrack/talking-colonists/raw/neoforge-1.21.1/imgs/ingame.png?raw=true)

Now the citizen will complain if they are unhappy or missing some resources, just talk right away!
 
## Can I include this in my modpack?
Yup