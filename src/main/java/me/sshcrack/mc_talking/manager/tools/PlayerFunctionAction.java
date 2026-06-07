package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.gemini_live_lib.gson.properties.Property;

public abstract class PlayerFunctionAction extends FunctionAction {
    private static final String PLAYER_ONLY_SUFFIX = "\n\nNOTE: This tool is ONLY callable when a player is speaking to you directly.";

    public PlayerFunctionAction(String name, String description) {
        super(name, description + PLAYER_ONLY_SUFFIX, null, true);
    }

    public PlayerFunctionAction(String name, String description, Property property) {
        super(name, description + PLAYER_ONLY_SUFFIX, property, true);
    }
}
