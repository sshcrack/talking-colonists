package me.sshcrack.mc_talking.internal.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.sshcrack.mc_talking.api.tool.AiToolParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Provider-neutral runtime validation for addon AI tool arguments. */
final class AiToolSchemaValidator {
    private AiToolSchemaValidator() {
    }

    static @Nullable String validate(@Nullable AiToolParameter schema, @Nullable JsonObject args) {
        if (schema == null) {
            if (args == null || args.size() == 0) return null;
            return "$: this tool does not accept parameters";
        }
        if (!(schema instanceof AiToolParameter.ObjectValue objectSchema)) {
            return "$: tool parameter schema must be an object";
        }
        JsonObject effective = args == null ? new JsonObject() : args;
        return validateObject(objectSchema, effective, "$");
    }

    static @NotNull String canonicalArguments(@Nullable JsonObject args) {
        return args == null ? "{}" : canonical(args);
    }

    private static @Nullable String validateValue(
            @NotNull AiToolParameter schema,
            @NotNull JsonElement value,
            @NotNull String path
    ) {
        if (value == JsonNull.INSTANCE || value.isJsonNull()) {
            return path + ": null is not allowed";
        }

        if (schema instanceof AiToolParameter.Primitive primitive) {
            if (!value.isJsonPrimitive()) return path + ": expected " + primitive.kind().name().toLowerCase();
            JsonPrimitive actual = value.getAsJsonPrimitive();
            return switch (primitive.kind()) {
                case STRING -> actual.isString() ? null : path + ": expected string";
                case BOOLEAN -> actual.isBoolean() ? null : path + ": expected boolean";
                case NUMBER -> actual.isNumber() ? null : path + ": expected number";
                case INTEGER -> isInteger(actual) ? null : path + ": expected integer";
            };
        }

        if (schema instanceof AiToolParameter.EnumValues enumeration) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                return path + ": expected enum string";
            }
            String actual = value.getAsString();
            return enumeration.values().contains(actual)
                    ? null
                    : path + ": expected one of " + enumeration.values();
        }

        if (schema instanceof AiToolParameter.Array array) {
            if (!value.isJsonArray()) return path + ": expected array";
            JsonArray values = value.getAsJsonArray();
            for (int i = 0; i < values.size(); i++) {
                String error = validateValue(array.items(), values.get(i), path + "[" + i + "]");
                if (error != null) return error;
            }
            return null;
        }

        if (schema instanceof AiToolParameter.ObjectValue object) {
            if (!value.isJsonObject()) return path + ": expected object";
            return validateObject(object, value.getAsJsonObject(), path);
        }

        return path + ": unsupported schema node";
    }

    private static @Nullable String validateObject(
            @NotNull AiToolParameter.ObjectValue schema,
            @NotNull JsonObject object,
            @NotNull String path
    ) {
        for (String supplied : object.keySet()) {
            if (!schema.properties().containsKey(supplied)) {
                return path + "." + supplied + ": unexpected parameter";
            }
        }

        for (var entry : schema.properties().entrySet()) {
            String name = entry.getKey();
            AiToolParameter child = entry.getValue();
            if (!object.has(name)) {
                if (child.required()) return path + "." + name + ": required parameter is missing";
                continue;
            }
            String error = validateValue(child, object.get(name), path + "." + name);
            if (error != null) return error;
        }
        return null;
    }

    private static boolean isInteger(JsonPrimitive primitive) {
        if (!primitive.isNumber()) return false;
        try {
            return new BigDecimal(primitive.getAsString()).stripTrailingZeros().scale() <= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String canonical(JsonElement element) {
        if (element == null || element.isJsonNull()) return "null";
        if (element.isJsonArray()) {
            List<String> values = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) values.add(canonical(child));
            return "[" + String.join(",", values) + "]";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            List<String> keys = new ArrayList<>(object.keySet());
            Collections.sort(keys);
            List<String> values = new ArrayList<>();
            for (String key : keys) {
                values.add(new JsonPrimitive(key) + ":" + canonical(object.get(key)));
            }
            return "{" + String.join(",", values) + "}";
        }
        return element.toString();
    }
}
