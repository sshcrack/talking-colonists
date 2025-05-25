package me.sshcrack.mc_talking.gson.properties;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class EnumProperty extends Property {
    @SerializedName("enum")
    private List<String> enumValues;

    public EnumProperty(List<String> values) {
        this(values, false);
    }

    public EnumProperty(List<String> values, boolean required) {
        super("string", required);
    }
}
