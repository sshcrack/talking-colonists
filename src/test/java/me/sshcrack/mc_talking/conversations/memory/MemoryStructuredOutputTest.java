package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryStructuredOutputTest {
    @Test
    void citizenConversationSchemaConstrainsCitizensTargetsTypesAndChangeRange() {
        var config = MemoryStructuredOutput.forCitizenConversation(List.of("Anna", "Tomas"));

        assertEquals("application/json", config.responseMimeType);
        JsonObject root = config.responseJsonSchema.getAsJsonObject();
        assertEquals("object", root.get("type").getAsString());
        assertFalse(root.get("additionalProperties").getAsBoolean());

        JsonObject citizenProperties = root.getAsJsonObject("properties")
                .getAsJsonObject("citizens")
                .getAsJsonObject("items")
                .getAsJsonObject("properties");
        assertTrue(citizenProperties.getAsJsonObject("name").getAsJsonArray("enum")
                .contains(new JsonPrimitive("Anna")));

        JsonObject relationshipProperties = citizenProperties.getAsJsonObject("memories")
                .getAsJsonObject("properties")
                .getAsJsonObject("relationships")
                .getAsJsonObject("items")
                .getAsJsonObject("properties");
        assertTrue(relationshipProperties.getAsJsonObject("target").getAsJsonArray("enum")
                .contains(new JsonPrimitive("Tomas")));
        assertTrue(relationshipProperties.getAsJsonObject("type").getAsJsonArray("enum").size() > 1);
        assertEquals(-1.0, relationshipProperties.getAsJsonObject("change").get("minimum").getAsDouble());
        assertEquals(1.0, relationshipProperties.getAsJsonObject("change").get("maximum").getAsDouble());
    }

    @Test
    void playerConversationSchemaUsesCitizenAndPlayerAsSeparateEnums() {
        var config = MemoryStructuredOutput.forPlayerConversation("Anna", "Steve");
        JsonObject citizenProperties = config.responseJsonSchema.getAsJsonObject()
                .getAsJsonObject("properties")
                .getAsJsonObject("citizens")
                .getAsJsonObject("items")
                .getAsJsonObject("properties");

        assertEquals("Anna", citizenProperties.getAsJsonObject("name").getAsJsonArray("enum").get(0).getAsString());
        JsonObject relationshipProperties = citizenProperties.getAsJsonObject("memories")
                .getAsJsonObject("properties")
                .getAsJsonObject("relationships")
                .getAsJsonObject("items")
                .getAsJsonObject("properties");
        assertEquals("Steve", relationshipProperties.getAsJsonObject("target").getAsJsonArray("enum").get(0).getAsString());
    }
}
