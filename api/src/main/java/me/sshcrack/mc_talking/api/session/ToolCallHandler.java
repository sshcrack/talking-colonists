package me.sshcrack.mc_talking.api.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

@FunctionalInterface
public interface ToolCallHandler {
    JsonElement onToolCall(String id, String name, JsonObject args);
}
