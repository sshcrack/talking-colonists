package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.gemini_live_lib.gson.properties.Property;

public abstract class GeneralFunctionAction extends FunctionAction {
    public GeneralFunctionAction(String name, String description) {
        super(name, description, false);
    }

    public GeneralFunctionAction(String name, String description, Property property) {
        super(name, description, property, false);
    }
}
