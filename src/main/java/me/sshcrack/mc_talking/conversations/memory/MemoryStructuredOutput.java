package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.sshcrack.gemini_live_lib.misc.GeminiFlash;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;

import java.util.Arrays;
import java.util.Collection;

/** Builds the JSON Schema used for persistent-memory extraction requests. */
final class MemoryStructuredOutput {
    private MemoryStructuredOutput() {
    }

    static GeminiFlash.GenerateContentRequest.GenerationConfig forCitizenConversation(Collection<String> participantNames) {
        return forContext(MemoryResponseParser.ValidationContext.citizenConversation(participantNames));
    }

    static GeminiFlash.GenerateContentRequest.GenerationConfig forPlayerConversation(String citizenName, String playerName) {
        return forContext(MemoryResponseParser.ValidationContext.playerConversation(citizenName, playerName));
    }

    static GeminiFlash.GenerateContentRequest.GenerationConfig forContext(MemoryResponseParser.ValidationContext context) {
        return GeminiFlash.GenerateContentRequest.GenerationConfig.json(schema(context));
    }

    static JsonObject schema(MemoryResponseParser.ValidationContext context) {
        JsonObject root = objectSchema();
        JsonObject rootProperties = new JsonObject();
        root.add("properties", rootProperties);
        root.add("required", strings("citizens"));

        JsonObject citizens = arraySchema();
        rootProperties.add("citizens", citizens);

        JsonObject citizen = objectSchema();
        citizens.add("items", citizen);
        citizen.add("required", strings("name", "memories"));
        JsonObject citizenProperties = new JsonObject();
        citizen.add("properties", citizenProperties);
        citizenProperties.add("name", enumStringSchema(context.citizenNames()));

        JsonObject memories = objectSchema();
        citizenProperties.add("memories", memories);
        JsonObject memoryProperties = new JsonObject();
        memories.add("properties", memoryProperties);

        JsonObject relationships = arraySchema();
        memoryProperties.add("relationships", relationships);
        JsonObject relationship = objectSchema();
        relationships.add("items", relationship);
        relationship.add("required", strings("target", "type", "change"));
        JsonObject relationshipProperties = new JsonObject();
        relationship.add("properties", relationshipProperties);
        relationshipProperties.add("target", enumStringSchema(context.relationshipTargets()));
        relationshipProperties.add("type", enumStringSchema(Arrays.stream(CitizenRelationshipDimension.values())
                .map(Enum::name)
                .toList()));

        JsonObject change = new JsonObject();
        change.addProperty("type", "number");
        change.addProperty("minimum", -1.0);
        change.addProperty("maximum", 1.0);
        relationshipProperties.add("change", change);

        memoryProperties.add("facts", stringArraySchema());
        memoryProperties.add("events", stringArraySchema());
        return root;
    }

    private static JsonObject objectSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject arraySchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "array");
        return schema;
    }

    private static JsonObject stringArraySchema() {
        JsonObject schema = arraySchema();
        JsonObject item = new JsonObject();
        item.addProperty("type", "string");
        schema.add("items", item);
        return schema;
    }

    private static JsonObject enumStringSchema(Collection<String> values) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        JsonArray allowed = new JsonArray();
        values.forEach(allowed::add);
        schema.add("enum", allowed);
        return schema;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) array.add(value);
        return array;
    }
}
