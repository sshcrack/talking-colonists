package me.sshcrack.mc_talking.api.provider;

import org.jetbrains.annotations.Nullable;

public record ConfigField(
    String key,
    ConfigType type,
    @Nullable Object defaultValue,
    int min,
    int max
) {
    public enum ConfigType {
        STRING,
        INTEGER,
        DOUBLE,
        BOOLEAN
    }
}
