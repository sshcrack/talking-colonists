package me.sshcrack.mc_talking.manager.tools;

import me.sshcrack.gemini_live_lib.gson.properties.ArrayProperty;
import me.sshcrack.gemini_live_lib.gson.properties.EnumProperty;
import me.sshcrack.gemini_live_lib.gson.properties.ObjectProperty;
import me.sshcrack.gemini_live_lib.gson.properties.PrimitiveProperty;
import me.sshcrack.gemini_live_lib.gson.properties.Property;
import me.sshcrack.mc_talking.api.tool.AiToolParameter;
import org.jetbrains.annotations.Nullable;

/** Converts the provider-neutral addon schema into Gemini Live Lib declarations. */
final class AiToolSchemaAdapter {
    private AiToolSchemaAdapter() {
    }

    static @Nullable Property toGemini(@Nullable AiToolParameter schema) {
        if (schema == null) return null;
        if (schema instanceof AiToolParameter.Primitive primitive) {
            PrimitiveProperty.Type type = switch (primitive.kind()) {
                case STRING -> PrimitiveProperty.Type.STRING;
                case NUMBER -> PrimitiveProperty.Type.NUMBER;
                case INTEGER -> PrimitiveProperty.Type.INTEGER;
                case BOOLEAN -> PrimitiveProperty.Type.BOOLEAN;
            };
            return new PrimitiveProperty(type, primitive.required());
        }
        if (schema instanceof AiToolParameter.EnumValues enumeration) {
            return new EnumProperty(enumeration.values(), enumeration.required());
        }
        if (schema instanceof AiToolParameter.Array array) {
            return new ArrayProperty(toGemini(array.items()), array.required());
        }
        if (schema instanceof AiToolParameter.ObjectValue object) {
            ObjectProperty result = new ObjectProperty();
            object.properties().forEach((name, child) -> result.addProperty(name, toGemini(child)));
            result.setRequired(object.required());
            return result;
        }
        throw new IllegalArgumentException("Unsupported addon AI tool schema: " + schema.getClass().getName());
    }
}
