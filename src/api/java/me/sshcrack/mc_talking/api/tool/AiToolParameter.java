package me.sshcrack.mc_talking.api.tool;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral parameter schema for an addon AI tool.
 *
 * <p>Talking Colonists translates this tree to the active model/provider declaration internally,
 * so addons do not need a compile-time dependency on Gemini Live Lib.</p>
 */
public sealed interface AiToolParameter permits AiToolParameter.Primitive, AiToolParameter.EnumValues,
        AiToolParameter.Array, AiToolParameter.ObjectValue {

    /** Whether the containing object should require this parameter. */
    boolean required();

    enum PrimitiveKind {
        STRING,
        NUMBER,
        INTEGER,
        BOOLEAN
    }

    record Primitive(@NotNull PrimitiveKind kind, boolean required) implements AiToolParameter {
        public Primitive {
            Objects.requireNonNull(kind, "kind");
        }
    }

    record EnumValues(@NotNull List<String> values, boolean required) implements AiToolParameter {
        public EnumValues {
            values = List.copyOf(values);
            if (values.isEmpty()) throw new IllegalArgumentException("enum values must not be empty");
            if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("enum values must be non-blank");
            }
        }
    }

    record Array(@NotNull AiToolParameter items, boolean required) implements AiToolParameter {
        public Array {
            Objects.requireNonNull(items, "items");
        }
    }

    record ObjectValue(@NotNull Map<String, AiToolParameter> properties, boolean required) implements AiToolParameter {
        public ObjectValue {
            Objects.requireNonNull(properties, "properties");
            LinkedHashMap<String, AiToolParameter> copy = new LinkedHashMap<>();
            properties.forEach((name, schema) -> {
                if (name == null || name.isBlank()) throw new IllegalArgumentException("property name must not be blank");
                copy.put(name, Objects.requireNonNull(schema, "schema for " + name));
            });
            properties = Map.copyOf(copy);
        }
    }

    static @NotNull Primitive string(boolean required) {
        return new Primitive(PrimitiveKind.STRING, required);
    }

    static @NotNull Primitive number(boolean required) {
        return new Primitive(PrimitiveKind.NUMBER, required);
    }

    static @NotNull Primitive integer(boolean required) {
        return new Primitive(PrimitiveKind.INTEGER, required);
    }

    static @NotNull Primitive bool(boolean required) {
        return new Primitive(PrimitiveKind.BOOLEAN, required);
    }

    static @NotNull EnumValues enumeration(@NotNull List<String> values, boolean required) {
        return new EnumValues(values, required);
    }

    static @NotNull Array array(@NotNull AiToolParameter items, boolean required) {
        return new Array(items, required);
    }

    static @NotNull ObjectValue object(@NotNull Map<String, AiToolParameter> properties) {
        return new ObjectValue(properties, false);
    }

    static @NotNull ObjectValue object(@NotNull Map<String, AiToolParameter> properties, boolean required) {
        return new ObjectValue(properties, required);
    }
}
