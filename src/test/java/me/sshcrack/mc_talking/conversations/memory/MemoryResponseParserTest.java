package me.sshcrack.mc_talking.conversations.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryResponseParserTest {
    private static final MemoryResponseParser.ValidationContext CONTEXT =
            MemoryResponseParser.ValidationContext.citizenConversation(List.of("Anna", "Tomas"));

    private static final String VALID = """
            {
              "citizens": [{
                "name": "Anna",
                "memories": {
                  "relationships": [{"target":"Tomas","type":"TRUST","change":0.25}],
                  "facts": ["I learned Tomas likes apples"],
                  "events": ["I talked with Tomas"]
                }
              }]
            }
            """;

    @Test
    void acceptsPlainAndSingleJsonFence() throws Exception {
        var plain = MemoryResponseParser.parse(VALID, CONTEXT);
        var fenced = MemoryResponseParser.parse("```json\n" + VALID + "\n```", CONTEXT);

        assertEquals("Anna", plain.citizens.get(0).name);
        assertEquals("Anna", fenced.citizens.get(0).name);
        assertEquals(0.25f, plain.citizens.get(0).memories.relationships.get(0).change);
    }

    @Test
    void missingOptionalCollectionsNormalizeToEmpty() throws Exception {
        var parsed = MemoryResponseParser.parse("""
                {"citizens":[{"name":"Anna","memories":{}}]}
                """, CONTEXT);

        var memories = parsed.citizens.get(0).memories;
        assertTrue(memories.relationships.isEmpty());
        assertTrue(memories.facts.isEmpty());
        assertTrue(memories.events.isEmpty());
    }

    @Test
    void rejectsProseEmbeddedJsonAndBrokenFence() {
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse("Here you go: " + VALID, CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse("```json\n" + VALID, CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(VALID + "\n```", CONTEXT));
    }

    @Test
    void rejectsNullRootMalformedAndUnknownFields() {
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse("null", CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse("{\"citizens\":[", CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse("{\"citizens\":[],\"extra\":true}", CONTEXT));
    }

    @Test
    void rejectsUnknownCitizenTargetAndRelationshipType() {
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(
                        VALID.replace("\"name\": \"Anna\"", "\"name\": \"Nobody\""), CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(
                        VALID.replace("\"target\":\"Tomas\"", "\"target\":\"Nobody\""), CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(VALID.replace("\"TRUST\"", "\"TELEPATHY\""), CONTEXT));
    }

    @Test
    void rejectsInvalidRelationshipNumbersAndNullCollections() {
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(VALID.replace("0.25", "1.01"), CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(VALID.replace("0.25", "-1.01"), CONTEXT));
        assertThrows(MemoryResponseParser.ValidationException.class,
                () -> MemoryResponseParser.parse(VALID.replace("\"facts\": [\"I learned Tomas likes apples\"]", "\"facts\": null"), CONTEXT));
    }
}
