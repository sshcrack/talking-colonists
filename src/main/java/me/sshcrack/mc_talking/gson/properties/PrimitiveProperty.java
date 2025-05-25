package me.sshcrack.mc_talking.gson.properties;

public class PrimitiveProperty extends Property{
    public PrimitiveProperty(Type type) {
        this(type, false);
    }

    public PrimitiveProperty(Type type, boolean required) {
        super(type.name().toLowerCase(), required);
    }

    public enum Type {
        STRING,
        NUMBER,
        INTEGER,
        BOOLEAN
    }
}
