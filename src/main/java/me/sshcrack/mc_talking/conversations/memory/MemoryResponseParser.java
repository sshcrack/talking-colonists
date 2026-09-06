package me.sshcrack.mc_talking.conversations.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import me.sshcrack.mc_talking.api.memory.CitizenRelationshipDimension;
import me.sshcrack.mc_talking.conversations.memory.gson.GsonMemoryResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict parser and validator for model-generated persistent-memory updates.
 *
 * <p>Only a plain JSON document or one enclosing Markdown code fence is accepted.
 * Required object fields must be present and correctly typed. The optional
 * {@code relationships}, {@code facts}, and {@code events} collections normalize to
 * empty lists when omitted; explicitly-null collections are rejected. Relationship
 * deltas must be finite and within [-1, 1]. Unknown relationship types, citizens, or
 * relationship targets reject the entire response before persistence begins.</p>
 */
public final class MemoryResponseParser {
    private static final Pattern FENCED_JSON = Pattern.compile(
            "\\A```(?:json)?[ \\t]*(?:\\R)?(.*?)(?:\\R)?```[ \\t]*\\z",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Set<String> ROOT_FIELDS = Set.of("citizens");
    private static final Set<String> CITIZEN_FIELDS = Set.of("name", "memories");
    private static final Set<String> MEMORY_FIELDS = Set.of("relationships", "facts", "events");
    private static final Set<String> RELATIONSHIP_FIELDS = Set.of("target", "type", "change");

    private MemoryResponseParser() {
    }

    public record ValidationContext(Set<String> citizenNames, Set<String> relationshipTargets) {
        public ValidationContext {
            citizenNames = Set.copyOf(citizenNames);
            relationshipTargets = Set.copyOf(relationshipTargets);
        }

        public static ValidationContext citizenConversation(Collection<String> participantNames) {
            Set<String> names = new LinkedHashSet<>(participantNames);
            return new ValidationContext(names, names);
        }

        public static ValidationContext playerConversation(String citizenName, String playerName) {
            return new ValidationContext(Set.of(citizenName), Set.of(playerName));
        }
    }

    public static final class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static GsonMemoryResponse parse(String rawResponse, ValidationContext context)
            throws ValidationException {
        if (rawResponse == null) throw new ValidationException("response is null");
        String jsonText = unwrapSingleFence(rawResponse);
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(jsonText);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new ValidationException("response is not valid JSON", exception);
        }
        if (rootElement == null || rootElement.isJsonNull() || !rootElement.isJsonObject()) {
            throw new ValidationException("root must be a JSON object");
        }

        JsonObject root = rootElement.getAsJsonObject();
        requireOnlyFields(root, ROOT_FIELDS, "root");
        JsonArray citizens = requireArray(root, "citizens", "root");
        GsonMemoryResponse response = new GsonMemoryResponse();
        response.citizens = new ArrayList<>();
        Set<String> seenCitizenNames = new HashSet<>();

        for (int i = 0; i < citizens.size(); i++) {
            String citizenPath = "citizens[" + i + "]";
            JsonObject citizenObject = requireObject(citizens.get(i), citizenPath);
            requireOnlyFields(citizenObject, CITIZEN_FIELDS, citizenPath);
            String name = requireNonBlankString(citizenObject, "name", citizenPath);
            if (!context.citizenNames().contains(name)) throw new ValidationException("unknown citizen '" + name + "'");
            if (!seenCitizenNames.add(name)) throw new ValidationException("duplicate citizen '" + name + "'");

            JsonObject memoriesObject = requireObject(requireField(citizenObject, "memories", citizenPath), citizenPath + ".memories");
            requireOnlyFields(memoriesObject, MEMORY_FIELDS, citizenPath + ".memories");
            GsonMemoryResponse.GsonMemoryData memories = new GsonMemoryResponse.GsonMemoryData();
            memories.facts = parseStringArray(memoriesObject, "facts", citizenPath + ".memories");
            memories.events = parseStringArray(memoriesObject, "events", citizenPath + ".memories");
            memories.relationships = parseRelationships(memoriesObject, context, citizenPath + ".memories");

            GsonMemoryResponse.GsonCitizenMemory citizen = new GsonMemoryResponse.GsonCitizenMemory();
            citizen.name = name;
            citizen.memories = memories;
            response.citizens.add(citizen);
        }
        return response;
    }

    private static String unwrapSingleFence(String rawResponse) throws ValidationException {
        String trimmed = rawResponse.trim();
        if (trimmed.isEmpty()) throw new ValidationException("response is empty");
        if (!trimmed.startsWith("```")) {
            if (trimmed.contains("```")) throw new ValidationException("Markdown fence must enclose the entire response");
            return trimmed;
        }
        Matcher matcher = FENCED_JSON.matcher(trimmed);
        if (!matcher.matches()) throw new ValidationException("invalid enclosing Markdown fence");
        String inner = matcher.group(1).trim();
        if (inner.isEmpty() || inner.contains("```")) throw new ValidationException("Markdown response must contain exactly one enclosing fence");
        return inner;
    }

    private static List<String> parseStringArray(JsonObject object, String field, String path) throws ValidationException {
        if (!object.has(field)) return new ArrayList<>();
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) throw new ValidationException(path + "." + field + " must be an array when present");
        List<String> values = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement value = array.get(i);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new ValidationException(path + "." + field + "[" + i + "] must be a string");
            }
            String normalized = value.getAsString().trim();
            if (normalized.isEmpty()) throw new ValidationException(path + "." + field + "[" + i + "] must not be blank");
            values.add(normalized);
        }
        return values;
    }

    private static List<GsonMemoryResponse.GsonRelationshipMemory> parseRelationships(JsonObject memories, ValidationContext context, String path) throws ValidationException {
        if (!memories.has("relationships")) return new ArrayList<>();
        JsonElement element = memories.get("relationships");
        if (element == null || element.isJsonNull() || !element.isJsonArray()) throw new ValidationException(path + ".relationships must be an array when present");
        List<GsonMemoryResponse.GsonRelationshipMemory> relationships = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            String relationshipPath = path + ".relationships[" + i + "]";
            JsonObject relationshipObject = requireObject(array.get(i), relationshipPath);
            requireOnlyFields(relationshipObject, RELATIONSHIP_FIELDS, relationshipPath);
            String target = requireNonBlankString(relationshipObject, "target", relationshipPath);
            if (!context.relationshipTargets().contains(target)) throw new ValidationException("unknown relationship target '" + target + "'");
            String rawType = requireNonBlankString(relationshipObject, "type", relationshipPath);
            CitizenRelationshipDimension type;
            try {
                type = CitizenRelationshipDimension.valueOf(rawType.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ValidationException("unknown relationship type '" + rawType + "'");
            }
            JsonElement changeElement = requireField(relationshipObject, "change", relationshipPath);
            if (!changeElement.isJsonPrimitive() || !changeElement.getAsJsonPrimitive().isNumber()) throw new ValidationException(relationshipPath + ".change must be a number");
            double change;
            try {
                change = changeElement.getAsDouble();
            } catch (NumberFormatException exception) {
                throw new ValidationException(relationshipPath + ".change must be a finite number", exception);
            }
            if (!Double.isFinite(change) || change < -1.0 || change > 1.0) throw new ValidationException(relationshipPath + ".change must be finite and within [-1, 1]");
            GsonMemoryResponse.GsonRelationshipMemory relationship = new GsonMemoryResponse.GsonRelationshipMemory();
            relationship.target = target;
            relationship.type = type;
            relationship.change = (float) change;
            relationships.add(relationship);
        }
        return relationships;
    }

    private static JsonElement requireField(JsonObject object, String field, String path) throws ValidationException {
        if (!object.has(field) || object.get(field) == null || object.get(field).isJsonNull()) throw new ValidationException(path + "." + field + " is required");
        return object.get(field);
    }

    private static JsonArray requireArray(JsonObject object, String field, String path) throws ValidationException {
        JsonElement value = requireField(object, field, path);
        if (!value.isJsonArray()) throw new ValidationException(path + "." + field + " must be an array");
        return value.getAsJsonArray();
    }

    private static JsonObject requireObject(JsonElement element, String path) throws ValidationException {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) throw new ValidationException(path + " must be an object");
        return element.getAsJsonObject();
    }

    private static String requireNonBlankString(JsonObject object, String field, String path) throws ValidationException {
        JsonElement value = requireField(object, field, path);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new ValidationException(path + "." + field + " must be a string");
        String string = value.getAsString().trim();
        if (string.isEmpty()) throw new ValidationException(path + "." + field + " must not be blank");
        return string;
    }

    private static void requireOnlyFields(JsonObject object, Set<String> allowed, String path) throws ValidationException {
        for (String field : object.keySet()) if (!allowed.contains(field)) throw new ValidationException(path + " contains unsupported field '" + field + "'");
    }
}
