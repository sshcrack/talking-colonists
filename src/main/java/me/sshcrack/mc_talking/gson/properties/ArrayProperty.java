package me.sshcrack.mc_talking.gson.properties;

public class ArrayProperty extends Property{
    private final Property items;

    public ArrayProperty(Property inner) {
        this(inner, false);
    }

    public ArrayProperty(Property items, boolean required) {
        super("array", required);
        this.items = items;
    }
}
