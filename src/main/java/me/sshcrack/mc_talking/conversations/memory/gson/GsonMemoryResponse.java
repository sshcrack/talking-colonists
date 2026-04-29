package me.sshcrack.mc_talking.conversations.memory.gson;

import com.google.gson.*;
import me.sshcrack.mc_talking.conversations.memory.data.CitizenRelationshipChangeType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class GsonMemoryResponse {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(CitizenRelationshipChangeType.class, new SafeEnumDeserializer())
            .create();

    public List<GsonCitizenMemory> citizens = new ArrayList<>();

    public static class GsonCitizenMemory {
        public String name;
        public GsonMemoryData memories = new GsonMemoryData();
    }

    public static class GsonMemoryData {
        public List<GsonRelationshipMemory> relationships = new ArrayList<>();
        public List<String> facts = new ArrayList<>();
        public List<String> events = new ArrayList<>();
    }

    public static class GsonRelationshipMemory {
        public String target;
        @Nullable
        public CitizenRelationshipChangeType type;
        public float change;
    }

    // Optional: Safe enum deserializer (prevents crashes on bad LLM output)
    public static class SafeEnumDeserializer implements JsonDeserializer<CitizenRelationshipChangeType> {
        @Override
        public CitizenRelationshipChangeType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return CitizenRelationshipChangeType.valueOf(json.getAsString());
            } catch (Exception e) {
                return null; // or default like TRUST
            }
        }
    }
}
