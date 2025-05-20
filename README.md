You can interactively talk to citizens in your minecolonies just as if they were alive!

## Setup Guide
0. Install all dependencies ([Minecolonies](https://www.curseforge.com/minecraft/mc-mods/minecolonies) and [Voicechat](https://modrinth.com/mod/simple-voice-chat))
1. Head to the [Google AI Studio](https://aistudio.google.com/u/1/apikey) and create an API key by pressing the blue button: ![Blue Create API button](https://cdn.modrinth.com/data/cached_images/d53f4cc29f085ff467768895e777f5f7918a6041.png)
2. Copy the token

3. Either the config file via text editor in `config/mc_talking-common.toml` or via the in-game GUI. Set the token and your desired [speaking language](https://ai.google.dev/gemini-api/docs/live#supported-languages):
 ```toml
 #This key is used to authenticate with the Gemini API. You can get one at https://aistudio.google.com/apikey
gemini_key = "Put your API key here"
 ```
(Notice that for the free tier only 3 concurrent users can talk to citizens)

## Usage
Craft a Citizen Communication Device using a Book and Quill and a Redstone Torch (I would really appreciate it if someone could create a better texture for it):
![Crafting Recipe](https://cdn.modrinth.com/data/cached_images/7bce3c5cc338a64ef59e66745046ed596ef8dd1a.png)
Then left click on a citizen you want to talk to and something similar to this will show:
![Citizen that can be talked to](https://cdn.modrinth.com/data/cached_images/579b05f1ffe168dd43ff948767b69dfeef803361.png)

Now the citizen will complain if they are unhappy or missing some resources, just talk right away!
 